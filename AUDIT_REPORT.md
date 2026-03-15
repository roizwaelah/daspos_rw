# Audit Proyek DasPos

Tanggal audit: 2026-03-15
Lingkup audit: review kode statis + verifikasi tooling build dasar.

## Ringkasan eksekutif

Audit menemukan beberapa risiko **tinggi** pada area autentikasi, validasi data restore, dan observabilitas error. Secara umum aplikasi sudah punya struktur modul yang cukup rapi, namun ada celah yang berisiko pada keamanan dan stabilitas produksi.

## Temuan utama

## 1) [HIGH] Login tidak melakukan autentikasi nyata
- Di `LoginActivity`, pengguna bisa masuk ke `HomeActivity` hanya dengan mengisi field non-kosong, tanpa verifikasi terhadap database/credential.
- Dampak: kontrol akses bisa dilewati dengan mudah.

**Bukti:** `app/src/main/java/com/daspos/feature/auth/LoginActivity.java`

**Rekomendasi:**
- Implementasikan autentikasi terhadap user repository (cek user ada + verifikasi password hash).
- Tambahkan rate limiting / lockout sederhana untuk brute force.

## 2) [HIGH] Register tidak menyimpan akun
- `RegisterActivity` saat ini hanya menampilkan toast "Akun berhasil dibuat" lalu kembali ke login, tanpa persistence.
- Dampak: alur auth tidak konsisten; pengguna mengira akun tersimpan padahal tidak.

**Bukti:** `app/src/main/java/com/daspos/feature/auth/RegisterActivity.java`

**Rekomendasi:**
- Simpan user baru ke penyimpanan permanen (Room).
- Simpan password dalam bentuk hash (Argon2id/bcrypt/scrypt via library yang kompatibel Android).

## 3) [HIGH] Restore backup menerima input tanpa guardrail memadai
- `restore()` melakukan `db.clearAllTables()` segera setelah validasi kunci dasar; jika struktur data salah pada langkah berikutnya, operasi bisa berakhir `INVALID` setelah data lama terhapus.
- Dampak: potensi kehilangan data akibat file restore rusak/invalid.

**Bukti:** `app/src/main/java/com/daspos/feature/settings/BackupRestoreHelper.java`

**Rekomendasi:**
- Lakukan parse + validasi penuh dulu di memori.
- Gunakan transaksi database atomik; commit hanya jika seluruh payload valid.
- Pertimbangkan membuat snapshot internal sebelum clear.

## 4) [MEDIUM] Query database di main thread
- `AppDatabase` menggunakan `.allowMainThreadQueries()`.
- Dampak: risiko jank/ANR saat data bertambah.

**Bukti:** `app/src/main/java/com/daspos/db/AppDatabase.java`

**Rekomendasi:**
- Hapus `allowMainThreadQueries()`.
- Pindahkan operasi DB ke background executor/coroutine + expose LiveData/Flow.

## 5) [MEDIUM] Exception handling menelan error (silent failure)
- Beberapa operasi backup/restore menangkap `Exception` dan mengembalikan status generik tanpa logging detail.
- Dampak: debugging insiden sulit, RCA lambat.

**Bukti:** `app/src/main/java/com/daspos/feature/settings/BackupRestoreHelper.java`

**Rekomendasi:**
- Tambahkan logging terstruktur (minimal message + stacktrace).
- Bedakan error I/O, parsing, schema mismatch, dan permission.

## 6) [LOW] Tooling build tidak siap diverifikasi di environment ini
- `./gradlew test` gagal dieksekusi (permission) dan saat dieksekusi via `bash ./gradlew test` hanya menampilkan pesan bahwa `gradle-wrapper.jar` belum tersedia.
- Dampak: CI/otomasi quality gate tidak bisa dijalankan konsisten.

**Rekomendasi:**
- Commit Gradle wrapper lengkap (`gradle/wrapper/gradle-wrapper.jar`).
- Pastikan `gradlew` executable (`chmod +x gradlew`) di repo.

## Prioritas tindak lanjut (2 minggu)
1. **Minggu 1**: perbaiki auth end-to-end (register/login + hashing + validasi).
2. **Minggu 1**: harden restore (transactional restore + full validation before wipe).
3. **Minggu 2**: hilangkan main-thread DB access.
4. **Minggu 2**: benahi wrapper + aktifkan minimal CI (`assembleDebug`, unit test).

## Cek yang dijalankan saat audit
- `./gradlew test`
- `bash ./gradlew test`
- `rg -n "(?i)(password|secret|apikey|token|TODO|FIXME)" app/src/main/java app/src/main/res | head -n 200`
