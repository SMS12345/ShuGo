# ShuGo — Parental Control App: AI Agent Reference

> **Purpose**: Quick-start reference for any AI agent. Read this before touching any file.  
> **Package**: `com.antigravity.parentalcontrol` | **App name**: ShuGo  
> **Stack**: Kotlin · View Binding (NO Compose) · Firebase Realtime DB · Google Sign-In  
> **Min SDK**: 33 | **Target SDK**: 34 | **Build**: Gradle Groovy DSL  

---

## 1. Auth & Startup Flow

```
LoginActivity  →  MainActivity  →  PairingActivity  →  Dashboard
     ↑                   ↑
 GoogleAuthHelper    AppModeManager
```

- `LoginActivity`: Google Sign-In via `GoogleAuthHelper`. On success, saves display name to `AppModeManager`, navigates to `MainActivity`.
- `MainActivity`: Auth guard (redirects to `LoginActivity` if not signed in). If mode already set, skips mode picker and goes straight to dashboard. Shows `cardParentMode` / `cardChildMode` cards when mode is `NONE`.
- `PairingActivity`: Shows child's 6-digit device ID (copy-able) **or** prompts parent to enter child's ID. On completion → respective dashboard.
- `GoogleAuthHelper` (`auth/`): Wraps Google Sign-In and Firebase Auth. `isSignedIn()`, `signIn(activity)`, `handleSignInResult(data)`, `signOut(context, callback)`.

---

## 2. App Modes & State

All state persisted via `AppModeManager` (SharedPreferences `parental_control_prefs`):

| Key | Method | Purpose |
|-----|--------|---------|
| `app_mode` | `getAppMode / setAppMode` | `PARENT`, `CHILD`, or `NONE` |
| `linked_child_id` | `getLinkedChildId / setLinkedChildId` | 6-digit child device ID (parent stores this) |
| `device_id` | `getDeviceId` | Auto-generated 6-digit ID for child device |
| `user_name` | `getUsername / setUsername` | Google display name |

`clearAll()` wipes everything on sign-out. Mode is cached in-memory (`@Volatile cachedMode`) to avoid SharedPrefs I/O on hot paths.

---

## 3. Dashboards

### Parent (`ParentDashboardActivity`)
- Uses Toolbar + `setSupportActionBar`. Menu: sign-out button (`R.menu.menu_dashboard`).
- Initializes `FirebaseRepository` with the **child's** linked ID.
- Starts `ParentAlertService` (foreground).
- Default fragment: `ParentHomeFragment` (grid of nav cards).
- `setCenterTitle(title)` — called by fragments to update toolbar title.
- Fragments: `ParentHomeFragment`, `AlertsFragment`, `AppsFragment`, `UsageFragment`.

### Child (`ChildDashboardActivity`)
- No action bar (`supportActionBar?.hide()`).
- Initializes `FirebaseRepository` with its **own** device ID.
- On create: uploads installed app list (background thread), schedules `UsageSyncWorker` (every 15 min), calls `checkPermissions()` + `checkBatteryOptimization()` + `checkDeviceAdmin()`.
- `onResume()`: refreshes permission status UI and triggers one-time usage sync if permission granted.
- Fragments: `UsageFragment`, `NotificationsFragment`, `HistoryFragment`.
- Permission statuses shown as `statusAccessibility`, `statusUsage`, `statusOverlay`, `statusDeviceAdmin` TextViews.

---

## 4. Firebase Repository (`FirebaseRepository`)

**Single object**. Must call `FirebaseRepository.init(deviceId)` before any read/write.

- DB URL: `https://parental-control-f6ee0-default-rtdb.firebaseio.com/`
- Path pattern: `devices/{deviceId}/`

### Nodes under `devices/{id}/`

| Node | Type | Writer | Reader |
|------|------|--------|--------|
| `usage/{yyyy-MM-dd}/{pkg_name}` | `Long` (ms) | Child (`UsageSyncWorker`) | Parent (`UsageFragment`) |
| `notifications/{pushId}` | `NotificationEvent` | Child (`NotificationCollectorService`) | Parent (`NotificationsFragment`) |
| `alerts/{pushId}` | `AlertEvent` | Child (`KeepAliveService` / triggers) | Parent (`AlertsFragment`) |
| `history/{pushId}` | `HistoryEvent` | Child (`BrowserScraper`, `YoutubeScraper`) | Parent (`HistoryFragment`) |
| `installed_apps` | `List<AppInfo>` | Child (`ChildDashboardActivity`) | Parent (`AppsFragment`, `UsageFragment`) |
| `blocked_apps/{pkg}` | `Boolean` | Parent (`AppsFragment`) | Child (`UsageMonitoringService`) |
| `settings/block_new_apps` | `Boolean` | Parent | Child (`AppUpdateReceiver`) |

> **Important**: Package names stored in Firebase replace `.` with `_` (Firebase key restriction). Always `replace(".", "_")` before writing and `replace("_", ".")` after reading.

### Key API
- `uploadUsageStats(date, map)` — writes usage for a date
- `uploadNotification(event)` / `uploadHistory(event)` / `uploadAlert(event)` — push new entries
- `uploadInstalledApps(apps)` — replaces entire `installed_apps` list
- `listenFor*(callback): ValueEventListener` — real-time listeners (store reference, remove in `onDestroyView`)
- `stopListening(pathSuffix, listener)` — always call this to avoid leaks
- `setAppBlocked(packageName, blocked)` — parent blocks/unblocks apps
- `cleanupOldData(days)` — deletes entries older than N days

---

## 5. Services & Background Work

### `UsageMonitoringService` (AccessibilityService)
- **Child only**. Disables itself if mode != CHILD.
- Listens to `blocked_apps` in Firebase → manages `OverlayManager` to show full-screen block screen and call `GLOBAL_ACTION_HOME`.
- Triggers `BrowserScraper` (debounced 2 s cooldown) for known browsers.
- Polls `YoutubeScraper` every 1.5 s while YouTube is active.
- Event-driven usage sync via `UsageSyncWorker` (debounced, max once per 5 min).

### `NotificationCollectorService` (NotificationListenerService)
- **Child only**. Captures notifications from all apps and calls `FirebaseRepository.uploadNotification()`.

### `KeepAliveService`
- Keeps the child app alive in background with a persistent foreground notification.

### `ParentAlertService`
- **Parent only**. Listens for new alerts in Firebase and posts push notifications.

### `UsageSyncWorker` (WorkManager `Worker`)
- Queries `UsageStatsManager` with IST timezone (midnight→now).
- Filters: only apps with a launcher intent, >0 ms, not systemui or launcher.
- Uploads to `usage/{yyyy-MM-dd}` with IST date string.
- Also triggers `cleanupOldData(7)`.
- Scheduled: periodic every 15 min + one-time on resume + event-driven from `UsageMonitoringService`.

### `OverlayManager`
- Draws a full-screen blocked overlay using `TYPE_APPLICATION_OVERLAY`.
- `showOverlay(onGoHome)` / `hideOverlay()` / `isShowing()`.

---

## 6. Scrapers (`services/scrapers/`)

| Scraper | Trigger | What it does |
|---------|---------|--------------|
| `BrowserScraper` | Known browser foreground | Reads URL bar via AccessibilityNodeInfo, uploads `HistoryEvent(type="WEB")` |
| `YoutubeScraper` | YouTube foreground, polled every 1.5 s | Reads video title via AccessibilityNodeInfo, uploads `HistoryEvent(type="YOUTUBE")` |

Both have `reset()` called when leaving the app.

---

## 7. Receivers

| Receiver | Trigger | Action |
|----------|---------|--------|
| `BootReceiver` | `BOOT_COMPLETED` | Restarts `KeepAliveService` if mode == CHILD |
| `AppUpdateReceiver` | `PACKAGE_ADDED` | If `settings/block_new_apps` is true, blocks the newly installed package |
| `ParentalDeviceAdmin` | Device Admin events | Prevents uninstall; minimal implementation |

---

## 8. Adapters & Item Layouts

| Adapter | Item Layout | Data |
|---------|-------------|------|
| `UsageAdapter` | `item_usage_card.xml` | `AppUsageItem` (packageName, appName, timeMs, maxTimeMs) |
| `NotificationAdapter` | `item_notification_card.xml` | `NotificationEvent` |
| `HistoryAdapter` | `item_history_card.xml` | `HistoryEvent` |
| `AlertAdapter` | `item_app_card.xml` | `AlertEvent` |
| `AppListAdapter` | `item_app_info.xml` | `AppInfo` + blocked state toggle |

---

## 9. Utility Classes

| Util | Purpose |
|------|---------|
| `AppListProvider` | `getInstalledApps(ctx)` — returns apps with launcher intent, excluding a small blocklist of noise system packages |
| `TimeUtils` | `formatDuration(ms)` → `"2h 30m"`, `getRelativeTime(ts)`, `formatDateTime(ts)` |
| `ColorUtils` | Generates color from package name for consistent app icon tinting |
| `IconFetcher` | Loads app icon via Glide; falls back to Play Store scrape via Jsoup |
| `IdCache` | `getDeviceId(ctx)` — quick wrapper around `AppModeManager.getDeviceId` used inside services |
| `ServiceUtils` | Helpers to start/stop services safely |
| `isAccessibilityServiceEnabled(ctx)` | Extension fun in utils — checks if `UsageMonitoringService` is enabled |
| `isNotificationListenerEnabled(ctx)` | Extension fun in utils — checks if `NotificationCollectorService` is enabled |

---

## 10. Models

```kotlin
data class NotificationEvent(id, packageName, title, text, timestamp)
data class AlertEvent(id, message, timestamp)
data class HistoryEvent(type, title?, url?, timestamp)   // type = "YOUTUBE" | "WEB"
data class AppInfo(packageName, appName, isSystemApp)
data class BlockedApp(packageName, isBlocked)
data class AppUsageItem(packageName, appName, timeMs, maxTimeMs)  // AppUsageItem.kt
```

---

## 11. Layout File Map

```
activity_login.xml           → LoginActivity
activity_main.xml            → MainActivity    (cardParentMode, cardChildMode)
activity_pairing.xml         → PairingActivity (llParentPairing, llChildPairing, tvMyCode, btnCopyCode)
activity_parent_dashboard.xml → ParentDashboardActivity (fragment_container + toolbar)
activity_child_dashboard.xml → ChildDashboardActivity  (status rows, btnFixPermissions)
fragment_parent_home.xml     → ParentHomeFragment       (grid cards for nav)
fragment_alerts.xml          → AlertsFragment
fragment_apps.xml            → AppsFragment
fragment_usage.xml           → UsageFragment            (usagePieChart, rvUsageList, date nav)
fragment_notifications.xml   → NotificationsFragment
fragment_history.xml         → HistoryFragment
item_*.xml                   → Adapter ViewHolders
overlay_app_blocked.xml      → OverlayManager
```

---

## 12. Coding Conventions

- **View Binding**: every screen uses `ActivityXBinding` / `FragmentXBinding`. No DataBinding. No Compose.
- **No direct UI in services**: Services post to main thread via `Handler(Looper.getMainLooper())`.
- **Listener cleanup**: always store `ValueEventListener` references and call `FirebaseRepository.stopListening()` in `onDestroyView` / `onDestroy`.
- **Package → Firebase key**: `replace(".", "_")` on write; `replace("_", ".")` on read.
- **IST timezone**: all date strings and usage queries use `TimeZone.getTimeZone("Asia/Kolkata")`.
- **Centralize Firebase**: all DB operations go through `FirebaseRepository`. Never access `FirebaseDatabase` directly from activities/fragments.
- **App mode guard**: every activity that is mode-specific calls `AppModeManager.getAppMode()` in `onCreate` and calls `finish()` if wrong mode.
- **Colors/Themes**: defined in `res/values/colors.xml` and `res/values/themes.xml`. Purple/violet is the primary brand color.

---

## 13. Dependencies (key)

| Library | Version | Use |
|---------|---------|-----|
| Firebase BOM | 34.9.0 | database, auth, analytics |
| Google Play Auth | 21.3.0 | Google Sign-In |
| MPAndroidChart | v3.1.0 | Pie chart in UsageFragment |
| Glide | 4.16.0 | Icon loading |
| Jsoup | 1.17.2 | Play Store icon/name scraping |
| WorkManager | AndroidX | Background usage sync |
| Material Components | AndroidX | Dialogs, BottomNav, Tabs |

---

## 14. tasks/ Folder

The `tasks/` directory at project root contains plain-text task files for the developer:
- `tasks/bugs` — list of known bugs to fix
- `tasks/tasks_2` — pending feature requests

These are NOT code files — they are developer notes/instructions.

---

## 15. Common Gotchas

1. **`FirebaseRepository.init(id)` must be called before any listener** — called in `onServiceConnected` (child service), `onCreate` of dashboards. If `deviceId == "unknown_device"`, uploads silently fail.
2. **Notification Listener check** — uses `NotificationManager.getEnabledListenerPackages()`. If service class name changes, the check will always return false.
3. **Usage stats timezone** — queries must start at IST midnight to avoid showing yesterday's data as today's.
4. **System apps in App Controls** — `AppListProvider` filters by `getLaunchIntentForPackage != null`, which still includes some unwanted system apps (Play Store, Personal Safety, etc.). Expand the `BLOCKLIST` set to exclude them.
5. **Overlay on blocked app** — `OverlayManager` uses `TYPE_APPLICATION_OVERLAY` (requires `SYSTEM_ALERT_WINDOW` permission). The permission request is handled in `ChildDashboardActivity.checkPermissions()`.
6. **Sign-out** — must call both `GoogleAuthHelper.signOut()` AND `AppModeManager.clearAll()` to fully reset state.
