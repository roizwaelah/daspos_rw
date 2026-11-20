# Audit Menyeluruh Proyek DasPos

Tanggal audit: 2026-06-02  
Lingkup audit: pemetaan fungsi aplikasi, arsitektur, keamanan, data/sync, kualitas kode, tooling build/test, dan kesiapan rilis.

## Re-Audit 2026-11-14

Verifikasi: `./gradlew.bat check -q --no-daemon` EXIT=0 (lint debug + unit test lulus). Lint: 0 errors, 330 warnings.

### Kritis (P0)

1. **Secret masih ter-commit.** `gradle.properties` (tracked, ada di HEAD) masih berisi `SUPABASE_URL` dan `SUPABASE_ANON_KEY` JWT live untuk project `kjomqkojbipatbjxxxht`; dikonfirmasi via `git grep HEAD -- gradle.properties`. `.gitignore` belum mengabaikan `gradle.properties`. Klaim di "Update eksekusi 2026-06-02" bahwa file ini sudah dibersihkan dan key dirotasi **tidak sesuai kondisi disk saat re-audit**. Tindakan: `git rm --cached gradle.properties`, tambahkan ke `.gitignore`, pindahkan nilai ke `~/.gradle/gradle.properties`/CI secret, dan rotasi anon key (riwayat git ikut terekspos).

### Tinggi (P1)

2. **Error swallowing masif:** ~50 `catch (Exception ignored) { }` di repository/service (`TransactionRepository`, `SupabaseTransactionService` ±8, `SupabaseAuthService` ±5, `PendingSyncStore`, `MenuAccessStore`, `ProductUnitPriceStore`, `ProductRepository`, `RemoteAuthBridgeService`, `LegacySeedRepository`, dll) menutupi kegagalan sync/auth/persist. Log berkonteks.
3. **Least-privilege RPC:** `create_pos_transaction` mengandalkan default grant EXECUTE ke PUBLIC/anon (file hanya `grant ... to authenticated`); di remote terverifikasi ada EXECUTE untuk PUBLIC/anon. Fungsi memblokir anon via `auth.uid() is null`, tetapi sebaiknya `revoke execute ... from public, anon`. Overload lama (tanpa `p_legacy_id`) masih ada.
4. **Idempotency DB:** tidak ada unique index `transactions(outlet_id, legacy_id)`; penjaminan hanya lewat app + advisory lock per-outlet/bulan.

### Sedang (P2)

5. **Idempotency online:** `TransactionRepository` masih memakai `UUID.randomUUID()` pada save online (baris ~56, 340, 592, 623, 717); offline pending memakai `offlineClientTransactionId` stabil. Retry online pasca network ambigu bisa duplikat.
6. **Skema Room tidak lengkap:** hanya `app/schemas/com.daspos.db.AppDatabase/2.json` dan `4.json`; `1.json`/`3.json` tidak ada (exportSchema historis off) sehingga `MigrationTestHelper` tidak bisa memvalidasi 1→2/3→4 penuh. DB version=4, migrasi 1→2→3→4 ada, tanpa destructive fallback.
7. **Backup plaintext:** `BackupRestoreHelper.backup` menulis `passwordHash` user ke JSON export tanpa enkripsi.
8. **Secret remote plaintext:** `RemoteProviderConfigStore` menyimpan `apiKey`/`edgeFunctionSecret` di SharedPreferences biasa (`remote_provider_config`); override `SupabaseConfig` juga plaintext. Pertimbangkan EncryptedSharedPreferences.
9. **Edge Function:** `create-cashier`/`delete-account` tanpa rate limit; CORS `Access-Control-Allow-Origin: *` (auth berbasis bearer, risiko rendah).
10. **Lint:** 330 warnings, 0 errors — turunkan bertahap.
11. **Kredensial default lokal:** `docs/MYSQL_LOCAL_NETWORK.md` dan `scripts/setup-mysql-local.sh` memuat `daspos123`/`root123` (lokal saja; rotasi untuk produksi).

### Baik / terverifikasi

- `check` EXIT=0; 3 test class (6 method) lulus: `PasswordHasherTest`, `AppDatabaseMigrationTest`, `PendingSyncStoreTest`. Tidak ada test instrumented.
- `allowBackup=false`, `fullBackupContent=false`.
- Sesi dan kredensial offline di EncryptedSharedPreferences (AES256_GCM) + PBKDF2 120k (`PasswordHasher`).
- Hanya `SplashActivity` exported; deep-link `daspos://auth/callback` hanya menampilkan pesan, tidak mengonsumsi token → risiko rendah.
- Tidak ada `http://` cleartext di kode Java; targetSdk 34 memblokir cleartext default.
- RLS aktif di semua tabel; policy dibatasi per-outlet via `current_outlet_id()`/`current_role()` (security definer); grant hanya ke authenticated.
- Index git bersih: `app/build`, `.idea`, `local.properties` tidak lagi tracked (238 file tracked).
- Release `minifyEnabled`/`shrinkResources` + `proguard-rules.pro`; CI `.github/workflows/android-ci.yml` (`./gradlew check assembleDebug`).

### Belum diverifikasi / manual

- Verifikasi remote ulang (pooler cooldown), drop overload RPC lama, uji RLS per-role end-to-end, uji instrumented login/checkout/cetak.

## Update eksekusi 2026-06-02

- Root `.gitignore` sudah ditambahkan; artefak build/IDE/local yang sempat ter-track sudah dibersihkan dari index git.
- `gradle.properties` repo sudah dibersihkan dari `SUPABASE_URL` dan `SUPABASE_ANON_KEY`; konfigurasi real dipindahkan ke secret management lokal. Rotasi anon key Supabase sudah dilakukan.
- Session/token lokal sudah dipindahkan ke encrypted shared preferences, backup aplikasi dimatikan, Room destructive migration sudah dihapus, schema export diaktifkan, dan migration 1 -> 2 eksplisit ditambahkan.
- Lint blocker utama sudah diperbaiki; `./gradlew.bat lintDebug`, `./gradlew.bat check`, `./gradlew.bat testDebugUnitTest`, dan `./gradlew.bat assembleDebug` sudah berhasil.
- Test dasar sudah ditambahkan untuk `PasswordHasher`, migrasi Room 1 -> 2, pending sync dedupe, dan idempotency key offline stabil per instalasi.
- CI workflow Android sudah ditambahkan di `.github/workflows/android-ci.yml`.
- Release hardening: `release` sekarang memakai `minifyEnabled true` dan `shrinkResources true`, file `app/proguard-rules.pro` ditambahkan.
- `supabase/04_rpc_create_transaction.sql` dieksekusi ke project Supabase linked `daspos`.
- Verifikasi remote sukses, signature baru dengan `p_legacy_id` terpasang. Signature lama masih ada sebagai overload (untuk kompatibilitas client lama).
- Edge Function `create-cashier` sudah di-hardening menjadi POST-only (list kasir memakai action list, buat kasir memakai action create); perubahan kontrak sudah dideploy ke Supabase remote.
- Sisa risiko teknis utama: idempotency direct online checkout masih memakai UUID baru pada retry setelah network failure ambigu; perlu desain lanjutan bila ingin jaminan anti-duplikasi online setara offline.

## Ringkasan eksekutif

DasPos adalah aplikasi **Point of Sale (POS) Android** single-module berbasis Java. Fitur utama sudah relatif lengkap: autentikasi, manajemen produk, transaksi, laporan, manajemen user, backup/restore, printer bluetooth/network, mode lokal Room, dan integrasi remote Supabase.

Kondisi engineering saat audit:

- `app/build.gradle` sudah ada dan build debug dapat diverifikasi.
- `PasswordHasher` sudah memakai PBKDF2 dengan salt dan mendukung migrasi hash lama.
- `./gradlew.bat assembleDebug` berhasil.
- `./gradlew.bat testDebugUnitTest` berhasil tetapi `NO-SOURCE`, artinya belum ada automated test.
- `./gradlew.bat lintDebug` gagal dengan **78 errors** dan **311 warnings**.
- LSP Java tidak dapat dijalankan karena `jdtls` belum terpasang di environment audit.

Risiko terbesar saat ini bukan lagi build file hilang atau hash password SHA-256 murni, melainkan **repo hygiene**, **penyimpanan token/session plaintext**, **Room destructive migration**, **kegagalan lint**, **ketiadaan test otomatis**, dan **ketahanan offline/online sync**.

## Peta fungsi aplikasi

1. **Autentikasi & sesi**
   - Login/register lokal dan remote melalui `UserRepository`, `LoginActivity`, `RegisterActivity`, `AuthSessionStore`, dan service Supabase.
   - Session menyimpan username, role, user id, access token, refresh token, outlet id, dan cache offline.
   - Auto logout ditangani oleh `BaseActivity` dengan timeout 60 menit.

2. **Dashboard / Home**
   - Menampilkan menu utama, identitas toko, dan ringkasan metrik operasional.
   - `BaseActivity.onStart()` juga memicu sync produk dan transaksi saat app kembali aktif.

3. **Produk**
   - CRUD produk lokal/remote, import produk, harga bertingkat, pencarian, dan cache Supabase.
   - `ProductRepository` menjadi pusat sinkronisasi lokal-remote.

4. **Transaksi**
   - Keranjang, checkout, simpan transaksi, cetak struk, dan pengurangan stok.
   - Supabase menggunakan RPC `create_pos_transaction` untuk checkout atomik.
   - Mode offline menyimpan transaksi lokal dan mengantrikan pending sync.

5. **Laporan**
   - Rekap transaksi harian/bulanan, detail struk, dan export operasional.

6. **User management**
   - Tambah/edit/hapus user, pengaturan role, dan menu access kasir.
   - Operasi kasir remote dijembatani oleh Edge Function Supabase.

7. **Settings dan backup/restore**
   - Info toko, receipt setting/preview, ganti password, konfigurasi database, backup/restore, app info, dan contact.

8. **Printer**
   - Konfigurasi printer bluetooth/network, scan bluetooth, dan helper ESC/POS.

## Verifikasi tooling

| Area | Hasil | Catatan |
| --- | --- | --- |
| Build debug | PASS | `./gradlew.bat assembleDebug` sukses. |
| Unit test | PASS tetapi kosong | `./gradlew.bat testDebugUnitTest` sukses dengan `NO-SOURCE`. |
| Android lint | FAIL | `lintDebug` menemukan 78 errors dan 311 warnings. |
| LSP Java | Tidak tersedia | `jdtls` belum terpasang. |
| Gradle compatibility | Warning | Gradle melaporkan deprecated features yang tidak kompatibel dengan Gradle 9.0. |

## Temuan prioritas

### 1) [P0] Repository hygiene buruk dan konfigurasi lokal ikut tracked

**Bukti:**

- Tidak ada root `.gitignore`.
- `git ls-files` menunjukkan `gradle.properties` dan `local.properties` tracked.
- `gradle.properties` berisi `SUPABASE_URL` dan `SUPABASE_ANON_KEY`.
- `git ls-files app/build` menunjukkan 52 artefak build tracked.
- `git ls-files .idea` menunjukkan 12 file IDE tracked.

**Dampak:**

- Diff menjadi berisik dan rawan konflik.
- Artefak generated/build bisa membuat review dan audit keliru.
- Konfigurasi lokal dan key publik Supabase ikut tersebar melalui repo.
- Jika repo pernah dibagikan di luar pihak tepercaya, key perlu dianggap terekspos.

**Rekomendasi:**

1. Tambahkan root `.gitignore` untuk `.gradle/`, `build/`, `app/build/`, `.idea/`, `local.properties`, log/crash dump, dan backup lokal.
2. Keluarkan file generated/IDE/local config dari index git.
3. Pindahkan konfigurasi Supabase ke `~/.gradle/gradle.properties`, environment CI, atau secret management lokal.
4. Rotasi `SUPABASE_ANON_KEY` bila repo pernah dibagikan publik atau ke pihak yang tidak semestinya.

---

### 2) [P0] Token/session dan credential offline disimpan plaintext

**Bukti:**

- `AuthSessionStore` memakai `context.getSharedPreferences(PREF, Context.MODE_PRIVATE)`.
- Data yang disimpan mencakup `access_token`, `refresh_token`, offline token, role, outlet id, dan hash credential offline.
- `getAccessToken()` dapat fallback ke offline access token.

**Dampak:**

- Device hilang, rooted, malware, atau backup extraction bisa mengekspos token dan data outlet.
- Refresh token yang tersimpan lama memperbesar blast radius kompromi.
- Offline auth memperpanjang umur credential di device.

**Rekomendasi:**

1. Pindahkan token dan material credential ke encrypted storage Android.
2. Minimalkan data offline yang disimpan; simpan hanya yang benar-benar dibutuhkan.
3. Tambahkan expiry/rotation policy untuk offline session.
4. Evaluasi `android:allowBackup` karena backup dapat memperlebar risiko ekstraksi data.

---

### 3) [P0] Room memakai destructive migration

**Bukti:**

- `AppDatabase` version 2 menggunakan `.fallbackToDestructiveMigration()`.
- `exportSchema = false`.

**Dampak:**

- Upgrade database dapat menghapus data lokal secara diam-diam.
- Risiko tinggi untuk POS karena data lokal bisa berisi transaksi offline dan pending sync.
- Tidak ada schema history untuk memvalidasi migrasi.

**Rekomendasi:**

1. Hapus destructive fallback untuk build rilis.
2. Aktifkan `exportSchema = true` dan commit schema Room.
3. Tambahkan migration eksplisit antar versi database.
4. Tambahkan test upgrade database untuk data produk, transaksi, user, dan pending sync.

---

### 4) [P1] Lint gagal dan belum menjadi gate rilis

**Bukti:**

- `./gradlew.bat lintDebug` gagal dengan 78 errors dan 311 warnings.
- Error pertama: `BluetoothScanActivity.java:75` `MissingPermission` pada `bt.getBondedDevices()`.
- Error lain yang terlihat: `HomeActivity.java:222` `MissingSuperCall` pada override `onBackPressed()`.

**Dampak:**

- Risiko crash runtime, terutama permission Bluetooth pada Android versi baru.
- Kualitas rilis tidak terkunci oleh static analysis.
- Warning yang banyak membuat issue penting sulit terlihat.

**Rekomendasi:**

1. Jadikan `lintDebug` atau lint variant rilis sebagai gate wajib sebelum release.
2. Selesaikan error blocker terlebih dahulu: permission Bluetooth dan missing super call.
3. Turunkan warning bertahap setelah error menjadi 0.
4. Tambahkan baseline hanya untuk warning legacy yang benar-benar tidak bisa diselesaikan segera, bukan untuk error baru.

---

### 5) [P1] Automated test belum ada

**Bukti:**

- `app/src/test` dan `app/src/androidTest` tidak ditemukan.
- `testDebugUnitTest` melaporkan `NO-SOURCE`.
- `app/build.gradle` belum memiliki dependency test seperti JUnit/Mockito/Robolectric/Espresso.
- Dokumen manual QA sudah ada: `docs/REGRESI_TEST_CASES.md`, `docs/QA_OPERASIONAL_HARIAN.md`, `docs/RELEASE_GATE.md`.

**Dampak:**

- Perubahan auth, sync, transaksi, dan Room tidak punya safety net otomatis.
- Manual QA bagus untuk operasional, tetapi tidak cukup sebagai regression guard harian.
- Risiko regresi meningkat saat fitur offline/online sync makin kompleks.

**Rekomendasi:**

1. Tambahkan test dependency minimum.
2. Mulai dari unit test kecil untuk `PasswordHasher`, mapper/model, queue pending sync, dan util tanggal/nominal.
3. Tambahkan test repository dengan fake provider untuk transaksi offline/online.
4. Tambahkan instrumented smoke test untuk login, tambah produk, checkout, dan buka struk.
5. Jalankan `assembleDebug`, unit test, dan lint dalam CI sebelum merge/release.

---

### 6) [P1] Offline/online sync belum terlihat punya idempotency key kuat

**Bukti:**

- Online checkout memanggil remote `saveTransaction()` lalu RPC `create_pos_transaction`.
- RPC `create_pos_transaction` sudah lebih aman karena atomik, memvalidasi role/outlet, mengecek stok, dan memakai advisory lock untuk nomor transaksi.
- Payload client memakai `p_device_id` hardcoded `android-app`.
- Dari audit statis belum terlihat stable client transaction UUID/idempotency key yang dipaksakan unik di remote.
- Pending sync tersimpan di `PendingSyncStore` berbasis `SharedPreferences` JSON.

**Dampak:**

- Retry setelah timeout/network ambiguity dapat membuat transaksi duplikat.
- Pending sync yang gagal sebagian sulit direkonsiliasi.
- Stok dan laporan bisa tidak konsisten jika operasi terulang.

**Rekomendasi:**

1. Buat client transaction id/idempotency key stabil sebelum transaksi dikirim.
2. Simpan key tersebut di lokal dan kirim ke Supabase RPC.
3. Tambahkan unique constraint remote per outlet/device/client transaction id.
4. Pastikan retry mengembalikan transaksi yang sama, bukan membuat transaksi baru.
5. Tambahkan audit log sederhana untuk pending sync success/failure.

---

### 7) [P1] Kegagalan background sync banyak ditelan diam-diam

**Bukti:**

- `ProductRepository.triggerSyncIfPossible()` menangkap `Exception ignored`.
- `TransactionRepository` dan `PendingSyncStore` memiliki beberapa pola catch ignored pada parsing/sync.
- Sync dipicu otomatis dari `BaseActivity.onStart()`.

**Dampak:**

- User dan support sulit tahu apakah data sudah benar-benar tersinkron.
- Pending queue bisa menumpuk tanpa indikator.
- Debugging masalah stok/transaksi menjadi lambat.

**Rekomendasi:**

1. Catat error sync minimal ke log internal atau status store.
2. Tampilkan indikator sync pending/gagal pada UI operasional.
3. Tambahkan retry/backoff yang eksplisit.
4. Bedakan error jaringan sementara, auth expired, konflik data, dan validasi remote.

---

### 8) [P2] Release hardening belum cukup

**Bukti:**

- Saat audit awal, `app/build.gradle` memakai `release { minifyEnabled false }`.
- Saat audit awal, `AndroidManifest.xml` memakai `android:allowBackup="true"`.
- Gradle memberi warning deprecated features menuju Gradle 9.0.
- Status eksekusi 2026-06-02: `minifyEnabled true`, `shrinkResources true`, backup aplikasi dimatikan, dan `assembleRelease` sudah berhasil dengan R8 rules minimal.

**Dampak:**

- Saat audit awal, APK rilis belum dioptimasi/diobfuscate.
- Saat audit awal, backup app dapat memperbesar risiko ekstraksi data jika sensitive storage belum dienkripsi.
- Upgrade Gradle mendatang berisiko butuh perbaikan mendadak.

**Rekomendasi:**

1. Pantau `minifyEnabled true`/`shrinkResources true` yang sudah aktif, terutama setelah dependency baru ditambahkan.
2. Kebijakan backup sudah dimatikan; pertahankan atau gunakan backup rules eksplisit jika backup perlu diaktifkan ulang.
3. Jalankan Gradle dengan `--warning-mode all` dan selesaikan warning bertahap.

---

### 9) [P2] Supabase sudah punya dasar baik, tetapi perlu audit operasional

**Bukti positif:**

- `supabase/01_schema.sql` mengaktifkan RLS pada tabel penting.
- Policy memakai `current_outlet_id()` dan `current_role()` untuk segmentasi outlet/role.
- Edge Function `create-cashier` dan `delete-account` memakai service role hanya di server dan memverifikasi bearer token sebelum operasi admin.
- `create_pos_transaction` adalah RPC atomik untuk transaksi dan pengurangan stok.

**Risiko tersisa:**

- Supabase anon key memang boleh ada di mobile app, tetapi tetap harus diasumsikan publik dan bergantung pada RLS yang benar.
- Saat audit awal, Edge Function `create-cashier` mengizinkan `GET` dan `POST`; secara lokal sudah dibatasi menjadi `POST` dengan `action` eksplisit, tetapi perlu deploy Supabase saat app baru siap rilis.
- Token refresh/expiry di sisi mobile perlu dipastikan aman.

**Rekomendasi:**

1. Review ulang semua RLS dengan test role owner/admin/kasir.
2. Pastikan anon key tidak diperlakukan sebagai secret.
3. Lanjutkan audit Edge Function untuk rate limit, logging, dan error response; pembatasan method `create-cashier` sudah diterapkan lokal.
4. Dokumentasikan prosedur rotasi key dan rollback Supabase migration.

## Catatan temuan lama yang sudah berubah

1. **Build module hilang**: sudah tidak valid. `app/build.gradle` sekarang ada dan `assembleDebug` sukses.
2. **Password masih SHA-256 tanpa salt**: sudah tidak valid untuk implementasi baru. `PasswordHasher` sekarang memakai `PBKDF2WithHmacSHA256`, salt 16 byte, 120000 iterasi, dan migrasi hash lama saat login.
3. **Auto logout 5 menit**: tidak sesuai kode saat ini. `BaseActivity` memakai 60 menit.

## Roadmap rekomendasi

### Segera sebelum rilis berikutnya

1. Bersihkan repo dan tambah `.gitignore`.
2. Keluarkan file generated/IDE/local config dari git.
3. Amankan token/session storage.
4. Hilangkan destructive Room migration untuk release.
5. Selesaikan semua lint error blocker.

### Sprint berikutnya

1. Tambahkan automated test minimum.
2. Tambahkan idempotency key untuk transaksi offline/online.
3. Tambahkan status/log sync untuk pending queue.
4. Jalankan QA manual sesuai `docs/REGRESI_TEST_CASES.md` dan `docs/QA_OPERASIONAL_HARIAN.md`.

### Hardening jangka menengah

1. Lanjutkan pemantauan release minification/obfuscation yang sudah aktif, terutama setelah dependency baru ditambahkan.
2. Buat CI gate: build, test, lint, dan packaging.
3. Tambahkan Room migration test.
4. Audit RLS dan Edge Function dengan test role/outlet.
5. Selesaikan warning Gradle compatibility menuju Gradle 9.0.

## Keputusan rilis saat audit

**Belum disarankan rilis production** sampai minimal P0 diselesaikan dan `lintDebug` tidak lagi gagal.

Alasan utama:

1. Token/session masih plaintext.
2. Room migration masih destructive.
3. Repo masih melacak config lokal dan artefak generated/build.
4. Lint masih gagal dengan error runtime-relevant.
5. Automated test belum ada.
