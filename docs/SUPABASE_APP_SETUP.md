# Supabase App Setup (Android)

Konfigurasi ini untuk mengaktifkan login Supabase pada app DasPos.

## 1) Isi konfigurasi

Konfigurasi bisa diisi langsung dari aplikasi:

1. Buka **Pilih Database**.
2. Pilih **Remote**.
3. Pilih provider **Supabase**.
4. Isi **Supabase URL** dan **Supabase Anon Key**.
5. Tekan **Simpan**.

Alternatif untuk build preset: isi `gradle.properties` (atau lebih aman di `~/.gradle/gradle.properties`):

```properties
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<your-anon-key>
```

## 2) Build aplikasi

```bash
./gradlew assembleDebug
```

## 3) Perilaku login saat ini

1. Jika Supabase URL dan Anon Key terisi dari aplikasi atau build config:
   - App mencoba login Supabase (`email + password`) terlebih dulu.
2. Jika gagal / belum terkonfigurasi:
   - App fallback ke login lokal Room (perilaku lama).

Catatan:
- Input username untuk login Supabase harus format email.
- Role diambil dari tabel `profiles.role` pada Supabase. Jika tidak ditemukan, default `admin`.
- Untuk sesi dengan source `supabase`, modul Produk (list/cari/tambah/edit/hapus/cek stok) sudah mengarah ke tabel `products` Supabase berdasarkan `profiles.outlet_id`.
