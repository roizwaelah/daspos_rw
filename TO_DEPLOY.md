# DasPos Supabase Setup — Panduan Deploy

Panduan deploy database & edge functions Supabase untuk aplikasi DasPos Android. File SQL ada di folder `supabase/`; dokumen ini ada di root project.

Semua file SQL idempotent (`if not exists` / `drop ... if exists`) — aman dijalankan berulang kali di SQL Editor. Jalankan **berurutan sesuai nomor**.

## File SQL (folder `supabase/`)

| File | Fungsi | Kapan dijalankan |
|------|--------|------------------|
| `supabase/01_schema.sql` | Skema inti: `outlets`, `profiles`, `products`, `transactions`, `transaction_items` + index, trigger `updated_at`, RLS policies, fungsi `current_role()`/`current_outlet_id()`, auto-provisioning profil (`bootstrap_profile_for_new_user`). Juga membuat `cashier_shifts` + `transactions.shift_id` — **sengaja dipertahankan** karena edge function `delete-account` dan guard di `10_purge_outlet` masih mereferensikannya, walau app tidak punya UI shift | **Selalu, pertama** |
| `supabase/02_migration_staging.sql` | Tabel staging `migration_*_stg` + fungsi `apply_migration_batch()` untuk impor data legacy | Hanya jika migrasi data lama |
| `supabase/03_rpc_create_transaction.sql` | RPC `create_pos_transaction` — checkout atomik (transaksi + items + potong stok + generate `trx_no`). **Superseded oleh `07`** — tetap jalankan agar urutan lengkap | **Selalu** |
| `supabase/04_auth_profile_hardening.sql` | Hardening RLS `profiles`: self-deactivate, hanya admin/owner yang kelola profil kasir | Direkomendasikan |
| `supabase/05_outlets_business_fields.sql` | Tambah `address`/`phone`/`email`/`updated_at` di `outlets` | **Skip untuk DB baru** — sudah termasuk di `01`. Hanya untuk project Supabase lama |
| `supabase/06_product_tier_fields.sql` | Kolom harga bertingkat `price_renteng/pak/karton` + `factor_renteng/pak/karton` di `products` | **Selalu** (DB lama) / sudah di `01` (DB baru) |
| `supabase/07_product_rw_fields_discount.sql` | Kolom `barcode`, `unit_name`, `cost_price`, `track_stock` di `products`; `discount` di `transactions`; recreate `create_pos_transaction` dengan `p_discount` + item `price` override | **Selalu** — wajib untuk sinkronisasi penuh fitur RW |
| `supabase/08_adjust_product_stock.sql` | RPC `adjust_product_stock` — penyesuaian stok atomik per delta (butuh `track_stock` dari `07`) | **Selalu** |
| `supabase/09_void_pos_transaction.sql` | RPC `void_pos_transaction` — hapus/void transaksi dan kembalikan stok item (butuh `track_stock` dari `07`) | **Selalu** |
| `supabase/10_purge_outlet.sql` | RPC `purge_outlet` — hapus seluruh data outlet dalam satu transaksi (dipakai edge function `delete-account`) | **Selalu** jika pakai purge outlet |
| `supabase/11_kasir_product_write.sql` | Perbarui RLS `products`: role `kasir` boleh insert/update/delete produk di outlet sendiri | **Selalu** untuk DB yang sudah deploy |
| `supabase/12_product_name_unique.sql` | Unique index `(outlet_id, lower(name))` di `products` — cegah nama produk duplikat antar device. **Gagal jika sudah ada duplikat** — jalankan audit query di header file dulu | **Selalu** untuk DB yang sudah deploy |
| `supabase/functions/create-cashier/` | Edge Function: admin buat akun kasir (`email_confirm=true`) + list kasir | Deploy via CLI |
| `supabase/functions/delete-account/` | Edge Function: hard delete akun / purge outlet | Deploy via CLI |

## Database Baru (mulai kosong)

Jalankan di **Supabase SQL Editor**, berurutan:

```
01_schema.sql
02_migration_staging.sql        (opsional — hanya jika migrasi data lama)
03_rpc_create_transaction.sql
04_auth_profile_hardening.sql
05_outlets_business_fields.sql  (skip — sudah termasuk di 01)
06_product_tier_fields.sql      (opsional — sudah termasuk di 01)
07_product_rw_fields_discount.sql
08_adjust_product_stock.sql
09_void_pos_transaction.sql
10_purge_outlet.sql
11_kasir_product_write.sql
12_product_name_unique.sql
```

Lalu deploy edge functions dari root project:

```powershell
supabase login
supabase link --project-ref <PROJECT_REF>
supabase functions deploy create-cashier
supabase functions deploy delete-account
```

Nama function harus sama dengan default di `app/build.gradle` (`create-cashier`, `delete-account`) atau override via properti Gradle/`local.properties`.

### Verifikasi cepat

- Signup user pertama → trigger membuat `outlets` + `profiles` role `admin` otomatis.
- Login → app membaca `profiles(role, outlet_id, is_active)` dan `outlets(name, address, phone, email)`.
- Checkout → `POST /rest/v1/rpc/create_pos_transaction` dengan `p_discount` dan `price` per item.

## Migrasi Data Legacy (dari backup JSON)

Selain file di atas, jalankan juga `02_migration_staging.sql`, lalu:

1. Generate SQL insert dari backup:

```powershell
python scripts/export_backup_to_staging_sql.py `
  --input <file-backup>.json `
  --output supabase/migration_batch_<batch>.sql `
  --batch-id <uuid-baru>
```

2. Jalankan `migration_batch_*.sql` di SQL Editor.

3. Apply ke tabel final (butuh `outlet_id` dan `profile_id` kasir/default yang valid):

```sql
select public.apply_migration_batch(
  '<batch-id>'::uuid,
  '<outlet-id>'::uuid,
  '<profile-id-kasir>'::uuid,
  'legacy-import'
);
```

4. (Opsional) Export staging ke CSV untuk import via dashboard:

```powershell
python scripts/convert_staging_sql_to_csv.py `
  --input supabase/migration_batch_<batch>.sql `
  --outdir supabase/csv_<batch>
```

### Batasan migrasi

- `migration_users_stg` (username + `password_hash` legacy) **tidak otomatis jadi akun login** — user harus dibuat manual via signup/edge function; `migration_user_map` tersedia untuk pemetaan manual `legacy_username → profile_id`.
- `apply_migration_batch` hanya memindahkan products, transactions, dan transaction_items. `price`/`stock` produk diisi dari backup; kolom lain (`cost_price`, `track_stock`, `price_ecer`) pakai default. Diskon tidak dimigrasikan lewat jalur staging ini.

## Skema yang Dipakai App (referensi)

- `products`: `id` (uuid), `outlet_id`, `legacy_id`, `name`, `barcode`, `price`, `cost_price`, `unit_name`, `stock`, `track_stock`, `price_ecer`, `price_renteng`, `price_pak`, `price_karton`, `factor_renteng`, `factor_pak`, `factor_karton`. Kolom `code` dan `category` ada di skema tapi tidak pernah ditulis app — legacy pasif.
- `transactions` (via RPC): `outlet_id`, `legacy_id`, `cashier_id`, `trx_no`, `trx_at`, `total`, `discount`, `pay`, `change_amount`, `device_id`, `sync_source`. Kolom `shift_id` ada tapi selalu `null` — tidak ada fitur shift di app.
- `transaction_items`: `transaction_id`, `product_id`, `legacy_product_id`, `product_name`, `price`, `qty`
- `profiles`: `id`, `full_name`, `role` (`owner`/`admin`/`kasir`), `outlet_id`, `is_active`
- `outlets`: `id`, `code`, `name`, `address`, `phone`, `email`

## Konfigurasi App

`gradle.properties` **tidak** lagi berisi kredensial — secrets dimuat dari `local.properties` (gitignored) atau properti Gradle `-P`. Untuk project Supabase berbeda, isi `SUPABASE_URL`, `SUPABASE_ANON_KEY`, dan nama function di `local.properties`. Key anon yang pernah ter-commit ke git history wajib di-rotate di dashboard Supabase.
