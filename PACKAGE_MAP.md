# Package Map

Root: `app/src/main/java/com/daspos/` — see `ARCHITECTURE.md` §2 for the full annotated directory map.

## App
- `com.daspos` (`DasPosApp` — Application class, lazy init + network sync trigger)

## Core
- `com.daspos.core.app` (MainActivity host, HomeFragment, BestSellerFragment, SplashActivity, BaseActivity)

## Features
- `com.daspos.feature.auth`
- `com.daspos.feature.product` (incl. tiered pricing form, CSV/Excel import)
- `com.daspos.feature.transaction` (POS cart, unit switching, manual items, discount, StrukFragment)
- `com.daspos.feature.report`
- `com.daspos.feature.user`
- `com.daspos.feature.settings` (incl. SelectDatabaseActivity provider spinner, backup/restore)
- `com.daspos.feature.printer` (Bluetooth/network ESC/POS)

## Data
- `com.daspos.db` (AppDatabase v6, RoomMapper, RoomConverters, RoomMigrationHelper)
- `com.daspos.db.dao` (OutboxDao, ProductDao, TransactionDao, UserDao)
- `com.daspos.db.entity` (Outbox/Product/User/Transaction/TransactionItem entities)
- `com.daspos.db.model` (TransactionWithItems)
- `com.daspos.repository` (OutboxStore facade, OutboxSyncWorker/Scheduler, ProductUnitPriceStore, Transaction/Product/User repositories)
- `com.daspos.repository.legacy` (AppRepository, LegacySeedRepository)
- `com.daspos.model` (Product, CartItem, SalesUnit, TransactionRecord, ReportItem, BestSellerItem, User)
- `com.daspos.remote` (RemoteDataProvider abstraction, factory, Supabase/Generic providers, RemoteProviderConfigStore, RemoteAuthBridgeService)
- `com.daspos.supabase` (SupabaseConfig, SupabaseHttp, Auth/SessionRefresher, Product/Transaction/Outlet services)

## Shared
- `com.daspos.shared.util` (DbExecutor, SecurePrefs, CurrencyUtils, PasswordHasher, NetworkUtils, etc.)
- `com.daspos.ui`
- `com.daspos.ui.state` (TransactionScreenState, ListUiState, ReportUiState, FormUiEffect, etc.)
- `com.daspos.viewmodel` (ViewModelFactoryHelper)
