# Supabase Migration Runbook (DasPos)

Dokumen ini untuk migrasi data DasPos lokal (Room/backup JSON) ke Supabase tanpa kehilangan data.

## 1) Persiapan

1. Pastikan semua kasir selesai transaksi.
2. Dari setiap device, lakukan export `daspos_backup.json`.
3. Simpan minimal 2 salinan backup per device.
4. Catat metadata per backup:
   - `device_id`
   - `outlet`
   - waktu backup
   - jumlah transaksi

## 2) Setup database Supabase

1. Jalankan [01_schema.sql](/C:/Users/AR/daspos_rw/supabase/01_schema.sql).
2. Jalankan [02_migration_staging.sql](/C:/Users/AR/daspos_rw/supabase/02_migration_staging.sql).
3. Jalankan [04_auth_profile_hardening.sql](/C:/Users/AR/daspos_rw/supabase/04_auth_profile_hardening.sql).
4. Jalankan sisa file migrasi **berurutan sesuai nomor** (`03`–`12`) — semuanya idempotent. `07_product_rw_fields_discount.sql` wajib untuk sinkronisasi barcode/satuan/modal/track_stock, diskon transaksi, dan harga tier.
4. Buat outlet awal (opsional jika belum ada):

```sql
insert into public.outlets (code, name)
values ('OUTLET-UTAMA', 'Outlet Utama')
returning id;
```

5. Untuk data lama, buat user auth di Supabase Auth (Dashboard), lalu isi `profiles`:

```sql
insert into public.profiles (id, full_name, role, outlet_id)
values
  ('<auth_user_uuid_owner>', 'Owner', 'owner', '<outlet_uuid>'),
  ('<auth_user_uuid_admin>', 'Admin', 'admin', '<outlet_uuid>'),
  ('<auth_user_uuid_kasir1>', 'Kasir 1', 'kasir', '<outlet_uuid>');
```

Catatan hardening:
- Akun signup baru akan otomatis dibuat sebagai `admin` + outlet baru (trigger di `04_auth_profile_hardening.sql`).
- Profile dengan role `kasir` hanya boleh dibuat/diubah/dihapus oleh role `admin/owner` dalam outlet yang sama (RLS policy).

## 3) Konversi backup JSON ke SQL staging

Gunakan script [export_backup_to_staging_sql.py](/C:/Users/AR/daspos_rw/scripts/export_backup_to_staging_sql.py):

```bash
python scripts/export_backup_to_staging_sql.py \
  --input /path/daspos_backup.json \
  --output /path/migration_batch.sql \
  --batch-id 11111111-1111-1111-1111-111111111111
```

Lalu jalankan file output `migration_batch.sql` di SQL Editor Supabase.

## 4) Apply dari staging ke tabel final

Karena backup lama tidak menyimpan kasir per transaksi, tentukan 1 user default untuk histori:

```sql
select public.apply_migration_batch(
  '11111111-1111-1111-1111-111111111111', -- batch_id
  '<outlet_uuid>',
  '<default_cashier_profile_uuid>',
  'legacy-device-01'
);
```

Ulangi langkah ini untuk setiap backup/device (gunakan `batch_id` berbeda).

## 5) Rekonsiliasi (wajib sebelum go-live)

Bandingkan angka backup vs Supabase:

```sql
-- total transaksi hasil migrasi
select count(*) from public.transactions where sync_source = 'legacy-import';

-- total omzet hasil migrasi
select coalesce(sum(total), 0) from public.transactions where sync_source = 'legacy-import';

-- total item terjual
select coalesce(sum(qty), 0) from public.transaction_items ti
join public.transactions t on t.id = ti.transaction_id
where t.sync_source = 'legacy-import';

-- transaksi duplikat by legacy_id (harus 0)
select outlet_id, legacy_id, count(*)
from public.transactions
where legacy_id is not null
group by outlet_id, legacy_id
having count(*) > 1;
```

Jika ada mismatch, jangan cutover dulu.

## 6) Cutover aman

1. Jadwalkan cutover setelah tutup kas.
2. Deploy app versi Supabase.
3. Selama 7-14 hari, simpan backup lokal lama (jangan dihapus).
4. Monitor:
   - login gagal
   - gagal simpan transaksi
   - duplikasi transaksi
   - selisih laporan harian

## 7) Checklist operasional

Gunakan checklist ini per outlet:

1. Semua device berhasil export backup.
2. Semua backup memiliki hash/checksum file.
3. `01_schema.sql` dan `02_migration_staging.sql` sukses dieksekusi.
4. User auth + `profiles` sudah lengkap.
5. Semua batch backup berhasil masuk staging.
6. `apply_migration_batch` sukses untuk setiap batch.
7. Rekonsiliasi count/sum transaksi cocok.
8. Tidak ada duplikasi `legacy_id`.
9. UAT transaksi live di minimal 2 device lolos.
10. Cutover disetujui owner.

## Catatan penting

1. Script default menyimpan `trx_at` sebagai UTC agar stabil saat import.
2. Jika perlu WIB, sesuaikan parser di script sebelum eksekusi massal.
3. Password hash legacy dipindahkan ke staging untuk audit, bukan untuk login Supabase Auth.
