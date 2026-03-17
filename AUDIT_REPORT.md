# Audit Menyeluruh Proyek DasPos

Tanggal audit: 2026-03-16  
Lingkup audit: pemetaan fungsi aplikasi + review kode statis + verifikasi tooling build lokal.

## Ringkasan eksekutif

Secara fungsional, proyek ini adalah aplikasi **Point of Sale (POS) Android** dengan cakupan fitur inti yang relatif lengkap: autentikasi, manajemen produk, transaksi, laporan, manajemen user, backup/restore, dan konfigurasi printer/struk.

Namun, ada blocker penting pada aspek operasional engineering: konfigurasi build Gradle belum lengkap di repository (module build file tidak ditemukan), sehingga audit tidak dapat memvalidasi kompilasi maupun test otomatis. Di sisi kode, ditemukan beberapa risiko keamanan dan performa yang perlu diprioritaskan.

## Peta fungsi aplikasi (berdasarkan kode)

1. **Autentikasi & sesi**
   - Login/register memakai `UserRepository` + `AuthSessionStore`.
   - Auto logout saat aplikasi di background > 5 menit ditangani oleh `BaseActivity`.

2. **Dashboard / Home**
   - Menampilkan identitas toko dan ringkasan metrik harian/bulanan (jumlah transaksi, pendapatan, jumlah produk).

3. **Produk**
   - Kelola produk + import produk.

4. **Transaksi**
   - Keranjang, checkout, cetak struk.

5. **Laporan**
   - Rekap data transaksi.

6. **User management**
   - Tambah/edit/hapus user.

7. **Settings**
   - Info toko, receipt setting/preview, ganti password, backup/restore, app info, contact.

8. **Printer**
   - Konfigurasi printer bluetooth/network + scanning bluetooth.

## Temuan audit

### 1) [HIGH] Konfigurasi build modul aplikasi tidak ditemukan
- `settings.gradle` mendeklarasikan module `:app`, tetapi file `app/build.gradle` atau `app/build.gradle.kts` tidak ada di repo.
- Dampak:
  - Build tidak bisa diverifikasi secara deterministik.
  - CI/CD tidak bisa dijalankan sampai struktur Gradle lengkap.

**Bukti:**
- `settings.gradle` meng-include `:app`.
- Hanya ada root `build.gradle`; module build file tidak ditemukan saat inspeksi filesystem.

**Rekomendasi:**
- Tambahkan `app/build.gradle` (atau `build.gradle.kts`) beserta konfigurasi Android plugin, SDK version, dependencies, buildTypes, dan test options.
- Tambahkan pipeline minimal: `assembleDebug` + unit test.

---

### 2) [MEDIUM] Hash password masih SHA-256 tanpa salt/KDF adaptif
- `PasswordHasher` menggunakan SHA-256 langsung.
- Dampak:
  - Rentan terhadap brute force offline jika database/hash bocor.
  - Tidak setara best practice modern untuk password storage.

**Bukti:** `app/src/main/java/com/daspos/shared/util/PasswordHasher.java`

**Rekomendasi:**
- Migrasi ke KDF adaptif (Argon2id/scrypt/bcrypt, sesuai dukungan Android/library).
- Tambahkan salt unik per user + parameter cost yang bisa ditingkatkan.
- Rancang migrasi hash bertahap (lazy migration saat login).

---

### 3) [MEDIUM] Potensi UI freeze karena pola `runBlocking` repository
- Repository memanggil database lewat `DbExecutor.runBlocking(...)`.
- Pola ini tetap blocking terhadap caller thread; jika dipanggil dari main thread akan berisiko jank/ANR pada data besar atau perangkat lambat.

**Bukti:**
- `app/src/main/java/com/daspos/shared/util/DbExecutor.java`
- Penggunaan di repository (`UserRepository`, `ProductRepository`, `TransactionRepository`).

**Rekomendasi:**
- Ubah API repository menjadi async/non-blocking (LiveData/Flow/coroutine callback pattern sesuai stack).
- Pastikan observer UI menerima state loading/success/error.

---

### 4) [MEDIUM] Alur startup belum memanfaatkan status sesi login
- `SplashActivity` selalu mengarahkan ke `LoginActivity`, tanpa branch berbasis session aktif.
- Dampak:
  - UX kurang mulus (pengguna yang masih login tetap selalu melihat login screen).
  - Menambah friction di alur penggunaan harian.

**Bukti:** `app/src/main/java/com/daspos/core/app/SplashActivity.java`

**Rekomendasi:**
- Tambahkan routing: jika `AuthSessionStore.hasSession(...)` true, arahkan langsung ke `HomeActivity`.
- Tetap pertahankan auto-logout timeout dari `BaseActivity` sebagai safety.

---

### 5) [POSITIVE] Mekanisme restore sudah lebih aman (transactional)
- Restore mem-parse payload lebih dulu lalu menjalankan `db.runInTransaction(...)` untuk clear + insert.
- Ini mengurangi risiko data setengah-tertulis saat restore gagal di tengah proses.

**Bukti:** `app/src/main/java/com/daspos/feature/settings/BackupRestoreHelper.java`

**Catatan peningkatan lanjutan:**
- Tambahkan checksum/signature file backup agar integritas bisa diverifikasi sebelum restore.

## Status verifikasi tooling

- Eksekusi `./gradlew testDebugUnitTest` gagal karena file wrapper script belum executable.
- Eksekusi `bash ./gradlew testDebugUnitTest` memulai download Gradle, tetapi gagal karena akses jaringan ke `services.gradle.org` ditolak proxy (HTTP 403).
- Karena modul `app` belum punya build file, sekalipun jaringan tersedia build tetap belum siap diverifikasi penuh.

## Prioritas tindak lanjut (7–14 hari)

1. **P0**: Lengkapi struktur Gradle module `app` + pastikan build lokal `assembleDebug` berjalan.
2. **P1**: Migrasi password storage ke KDF adaptif + rencana migrasi hash lama.
3. **P1**: Refactor repository agar non-blocking untuk UI thread safety.
4. **P2**: Perbaiki startup routing berbasis session aktif di splash.
5. **P2**: Tambah test minimal (repository/auth/backup-restore parser) untuk regression safety.

## Perintah audit yang dijalankan

- `rg --files`
- `find . -maxdepth 3 -name 'build.gradle*' -print`
- `./gradlew testDebugUnitTest`
- `bash ./gradlew testDebugUnitTest`
- inspeksi file inti dengan `sed -n` (manifest, activity, repository, helper)
