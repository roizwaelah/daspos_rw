# DasPos

Aplikasi Point of Sale (POS) Android untuk usaha ritel — kasir, produk dengan harga bertingkat, transaksi offline-first, dan sinkronisasi cloud opsional via Supabase.

- **Package**: `com.daspos` · **minSdk 22** · **targetSdk 34** · Java 17, Android Views + Material Components
- **Arsitektur**: single-Activity + Fragment, Room local-first dengan outbox sync via WorkManager

## Fitur Utama

- **Kasir / POS** — cart, diskon transaksi, item manual, struk + cetak ESC/POS (Bluetooth/network)
- **Harga bertingkat** — satuan ECER / RENTENG / PAK / KARTON dengan faktor konversi ke stok base unit
- **Produk** — barcode, satuan, harga modal, tracking stok opsional, import CSV/Excel (12 kolom terpetakan)
- **Manajemen user** — role `owner` / `admin` / `kasir` dengan menu access per kasir
- **Laporan** — paginated, filter periode, export
- **Backup/restore** — format JSON kaya (formatVersion 2)
- **Multi-provider remote** — Supabase (default) + provider generik (Firebase, Appwrite, PocketBase, Custom API)
- **Offline-first** — transaksi & produk tetap jalan tanpa internet; outbox Room disinkronkan otomatis saat online

## Build

```powershell
# butuh local.properties berisi sdk.dir (Android SDK)
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
.\gradlew lintDebug
```

## Konfigurasi Supabase (wajib untuk mode remote)

Secrets **tidak** di-commit. Isi di `local.properties` (gitignored):

```properties
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
SUPABASE_DELETE_ACCOUNT_FUNCTION=delete-account
SUPABASE_CREATE_CASHIER_FUNCTION=create-cashier
```

Setup database & edge functions → lihat **[TO_DEPLOY.md](TO_DEPLOY.md)** dan **[docs/SUPABASE_MIGRATION_RUNBOOK.md](docs/SUPABASE_MIGRATION_RUNBOOK.md)**.

## Dokumentasi

| File | Isi |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Arsitektur detail, lapisan data, alur sync |
| [PACKAGE_MAP.md](PACKAGE_MAP.md) | Peta package `com.daspos.*` |
| [TO_DEPLOY.md](TO_DEPLOY.md) | Panduan deploy Supabase (SQL `01`–`12` + edge functions) |
| [docs/REGRESI_TEST_CASES.md](docs/REGRESI_TEST_CASES.md) | Test case regresi manual |
| [docs/QA_OPERASIONAL_HARIAN.md](docs/QA_OPERASIONAL_HARIAN.md) | QA operasional harian |
| [docs/RELEASE_GATE.md](docs/RELEASE_GATE.md) | Checklist sebelum release |

## Struktur Singkat

```
app/src/main/java/com/daspos/
├── core/app/          # MainActivity host + boundary Activities
├── feature/           # Fragment + ViewModel per domain (auth, product,
│                      #   transaction, report, user, settings, printer)
├── db/                # Room AppDatabase v6 + entity/DAO/outbox
├── repository/        # Outbox sync, repositories, legacy seed
├── remote/            # Abstraksi multi-provider remote
├── supabase/          # Klien Supabase (auth, http, services)
└── shared/util/       # DbExecutor, SecurePrefs, helpers
```

Detail lengkap: [ARCHITECTURE.md](ARCHITECTURE.md) §2.
