# Migrasi Password ke KDF Adaptif

## Perubahan implementasi

Aplikasi sekarang menyimpan password dengan format hash versi baru:

`pbkdf2_sha256$<iterations>$<salt_base64>$<derived_key_base64>`

Detail parameter saat ini:
- Algoritma: `PBKDF2WithHmacSHA256`
- Iterasi: `120000`
- Salt: `16 byte` acak per user
- Panjang key turunan: `256 bit`

## Kompatibilitas hash lama

Hash lama (`SHA-256` heksadesimal 64 karakter) masih didukung saat login:
1. Verifikasi login mencoba format baru terlebih dulu.
2. Jika bukan format baru, sistem fallback ke verifikasi hash lama.
3. Jika login berhasil dan hash terdeteksi lama, password otomatis di-rehash ke format baru lalu disimpan ulang.

Dengan alur ini, tidak ada reset massal password saat rilis.

## Rencana rollout migrasi

1. **Rilis versi kompatibel (sekarang)**
   - Semua password baru langsung memakai KDF adaptif.
   - User lama dimigrasikan bertahap saat login berhasil.

2. **Monitoring adopsi**
   - Pantau proporsi akun dengan prefix `pbkdf2_sha256$` dari backup/ekspor database.
   - Tetapkan target, misalnya >95% akun aktif sudah format baru.

3. **Forced migration untuk akun jarang login (opsional)**
   - Jalankan kampanye "ganti password" untuk akun yang lama tidak login.
   - Atau saat restore backup lama, minta user login ulang agar hash ikut ter-upgrade.

4. **Peningkatan biaya KDF di masa depan**
   - Jika hardware makin cepat, naikkan nilai iterasi.
   - Mekanisme `needsRehash` akan otomatis upgrade hash lama (iterasi lebih rendah) saat login sukses.

5. **Sunset dukungan hash lama**
   - Setelah cakupan migrasi tinggi dan melewati masa grace period, fallback SHA-256 bisa dihapus.

## Catatan keamanan

- Tidak menyimpan password plaintext kapan pun.
- Perbandingan hash format baru memakai `MessageDigest.isEqual` (constant-time compare).
- Setiap user punya salt unik untuk menahan serangan rainbow table.
