# NotifyHub — Phase 1: Android Notification Collector

Native Android app that captures notification events through `NotificationListenerService`, stores them in a
local Room queue, and has WorkManager-based sync ready for a future self-hosted backend (Phase 2).

## Stack and versions (pinned in `gradle/libs.versions.toml`)
| Component | Version |
|---|---|
| Android Gradle Plugin / Gradle | 8.7.3 / 8.9 |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.28 |
| Compose BOM (Material 3) | 2024.12.01 |
| Room | 2.6.1 |
| WorkManager | 2.10.0 |
| OkHttp | 4.12.0 |
| kotlinx.serialization / coroutines | 1.7.3 / 1.9.0 |
| DataStore, Navigation, Lifecycle, Activity | 1.1.1, 2.8.5, 2.8.7, 1.9.3 |

compileSdk/targetSdk 35, minSdk 26, JDK 17. These were chosen from memory and **not resolved here**; if Gradle
reports a missing version, bump it in the catalog.

## Android Studio setup
1. Install Android Studio (a release supporting AGP 8.7+) with Android SDK Platform 35.
2. **File → Open** the `NotifyHub` folder and let Gradle sync.

## Build, install
```
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # with a device connected and USB debugging on
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedDebugAndroidTest   # Room tests, needs a device/emulator
```
Or press Run in Android Studio with a real device selected.

## Grant notification access
Open the app → **Access** tab → *Open notification access settings* → enable NotifyHub.
Sideloaded app on Android 13+ with the toggle greyed out: **Settings → Apps → NotifyHub → ⋮ → Allow restricted settings**, then retry.

## Test collection
Send yourself a notification from a device-connected shell:
```
adb shell cmd notification post -S bigtext -t "Hello" test-tag "This is a long test message"
```
Open **History**: a POSTED row appears. Swipe the notification away: a REMOVED row appears.
Excluding an app: **Apps** tab → switch the app off, then trigger another notification from it (nothing is stored).
Restart check: force-stop NotifyHub, reopen, History still lists the events.
(`adb shell` notifications come from `com.android.shell`; exclude it to test exclusions.)

## Configure the future backend
**Server** tab: HTTPS URL, device ID (random UUID by default, editable), credential (encrypted with an Android Keystore key).
Plain `http://` is refused unless "Allow HTTP" is on, and it only works in debug builds (network security config).
Provisional contract used by the worker (Phase 2 may change it):
`POST {url}/api/v1/events` with `{deviceId, events:[...]}` and `Authorization: Bearer <credential>`; `GET {url}/api/v1/health` for the connection test.
With no server configured, events simply stay PENDING. Retries use WorkManager exponential backoff (30 s base), failed events retry up to 10 attempts.

## Architecture
`collector/` listener service → `NotificationExtractor` (Android → `RawNotification`) → `NotificationParser` (pure) →
`CollectionPipeline` (pause / exclusion / dedup) → Room (`data/db`). `sync/` has `SyncWorker`, `SyncScheduler`, `UrlPolicy`, `HttpSyncTransport`.
`ui/` is Compose + MVVM (ViewModels per screen). Delivery status: PENDING → SENDING → DELIVERED / FAILED; rows stuck in SENDING are reset on the next worker run.
Duplicates: each event has a SHA-256 over its identifying content with a unique index, so an identical repeated callback is stored once; an update with changed content is stored as a new event.

## Privacy
Notification content is never logged. No credentials are hardcoded. Pause, per-app exclusions and "Delete all" are in the app.
Release builds set `FLAG_SECURE`. The app does not bypass Android redaction of sensitive notifications.

## Known Android limitations
- Only notifications posted/removed while the listener is connected are seen; no history before access was granted.
- Android may redact sensitive content (e.g. OTP notifications on recent versions); redacted fields arrive empty or masked.
- Apps with custom `RemoteViews` layouts may expose no title/text extras; those fields are null.
- OEM battery managers can stop the listener; exempt the app from battery optimization if events go missing. Android rebinds the service after reboot once access is granted.
- Before the first unlock after a reboot, credential-encrypted storage is unavailable, so notifications in that window are missed.
- The app list shows launcher apps (no `QUERY_ALL_PACKAGES`); other notifying packages appear only after you exclude them by package.
- Removal events carry Android's reason code; `com.android.shell` and system packages are collected unless excluded.

## Acceptance status
See the chat report; items 1-10 require a build and a real device and are **unverified** until you run them.

## Building without Android Studio (GitHub Actions)
Push this folder to a GitHub repository. `.github/workflows/build.yml` runs the unit tests and `assembleDebug`;
download the `notifyhub-debug-apk` artifact from the run's page (Actions tab).
