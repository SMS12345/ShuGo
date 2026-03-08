# Parental Control Android App

## Project Overview
Android parental control app built with **Kotlin** using **View Binding** (not Compose). Uses **Firebase Realtime Database** for parent-child communication and **Firebase Auth + Google Sign-In** for authentication.

**Package**: `com.antigravity.parentalcontrol`
**Min SDK**: 33 | **Target SDK**: 34 | **Build System**: Gradle (Groovy DSL)

## Architecture

### Auth Flow
`LoginActivity` → Google Sign-In via `GoogleAuthHelper` → `MainActivity` (mode selector) → `PairingActivity` (6-digit code) → Dashboard

### App Modes
Managed by `AppModeManager` using SharedPreferences:
- **PARENT mode** → `ParentDashboardActivity` with fragments: `ParentHomeFragment`, `AlertsFragment`, `AppsFragment`
- **CHILD mode** → `ChildDashboardActivity` with fragments: `UsageFragment`, `NotificationsFragment`, `HistoryFragment`

### Key Components

| Layer | Files | Purpose |
|-------|-------|---------|
| **Activities** | `MainActivity`, `LoginActivity`, `PairingActivity`, `ParentDashboardActivity`, `ChildDashboardActivity` | Screen navigation & lifecycle |
| **Fragments** | `ParentHomeFragment`, `AlertsFragment`, `AppsFragment`, `UsageFragment`, `NotificationsFragment`, `HistoryFragment` | Tab content in dashboards |
| **Adapters** | `AlertAdapter`, `AppListAdapter`, `HistoryAdapter`, `NotificationAdapter`, `UsageAdapter` | RecyclerView adapters |
| **Services** | `UsageMonitoringService`, `NotificationCollectorService`, `OverlayManager`, `ParentAlertService`, `KeepAliveService` | Background services |
| **Scrapers** | `BrowserScraper`, `YoutubeScraper` | Content scraping from notifications |
| **Receivers** | `AppUpdateReceiver`, `BootReceiver`, `ParentalDeviceAdmin` | Broadcast receivers |
| **Repository** | `FirebaseRepository` | All Firebase database operations |
| **Auth** | `GoogleAuthHelper` | Google Sign-In + Firebase Auth |
| **Utils** | `AppListProvider`, `ColorUtils`, `IconFetcher`, `IdCache`, `ServiceUtils`, `TimeUtils` | Shared utilities |
| **Workers** | `UsageSyncWorker` | WorkManager periodic sync |
| **Models** | `Models.kt`, `AppUsageItem.kt` | Data classes |

### Firebase Database Structure
- Parent and child link via 6-digit pairing code
- Child device uploads: app usage, notifications, browsing history
- Parent device reads child data and manages alerts/app blocking

### Dependencies
- Firebase BOM 34.9.0 (database, auth, analytics)
- Google Play Services Auth 21.3.0
- AndroidX (AppCompat, Material, ConstraintLayout, WorkManager, SwipeRefreshLayout)
- Jsoup 1.17.2 (Play Store scraping for app icons/names)
- Glide 4.16.0 (image loading)
- MPAndroidChart v3.1.0 (usage charts)

## Conventions
- All layouts use View Binding (NOT DataBinding, NOT Compose)
- Layout files: `activity_*.xml`, `fragment_*.xml`, `item_*.xml`
- Colors defined in `res/values/colors.xml`
- Styles/themes in `res/values/themes.xml`
- Firebase operations centralized in `FirebaseRepository`
- App state (mode, username, linked ID) stored in `AppModeManager` via SharedPreferences
- Background services managed through `ServiceUtils`
