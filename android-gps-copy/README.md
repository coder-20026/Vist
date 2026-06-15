# GPS Coordinate Copy

A tiny, **native Android (Kotlin)** app whose only job is to put your current GPS
coordinate on screen the moment you turn Location ON, so you can copy it instantly.

Output format is exactly:

```
22.1234,71.5628
```

`Latitude,Longitude` · 4 decimals · no space after the comma · no labels, no maps,
no address, no accuracy, no extras.

---

## How it works

1. The app stays **dormant**. It does **no** background tracking and **no** polling.
2. A manifest `BroadcastReceiver` listens only for the system
   `android.location.PROVIDERS_CHANGED` event (this broadcast is exempt from
   Android's implicit-broadcast restrictions, so it works while the app is asleep).
3. When Location/GPS turns **ON**, a short-lived foreground service:
   - shows `Getting location...`
   - requests **one fresh** high-accuracy fix via the Fused Location Provider
     (cached/old fixes are rejected, bad fixes below ~100 m accuracy are ignored),
   - updates the same notification to show only the coordinate,
   - then immediately goes idle again. Total awake time: a few seconds.
4. The notification has two buttons:
   - **COPY** → copies the coordinate, shows a `Copied` toast, light vibration,
     notification disappears.
   - **DISMISS** → notification disappears, nothing copied.
5. If you ignore it for **10 minutes**, the coordinate is auto-copied to the
   clipboard and the notification is removed (no popup, no screen).
6. One fixed notification ID → notifications never stack; a new one replaces the old.
   Identical coordinates on repeated ON/OFF do not create duplicate notifications.

The app UI is a single line: **Monitoring: Active / Inactive**.

---

## Project structure

```
android-gps-copy/
├── build.gradle                 (root)
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── .github/workflows/android.yml   (CI: builds the APK)
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/personal/gpscopy/
        │   ├── Constants.kt
        │   ├── CoordinateFormatter.kt      (exact "lat,lng" formatting)
        │   ├── LocationStore.kt            (SharedPreferences state)
        │   ├── NotificationHelper.kt
        │   ├── ProvidersChangedReceiver.kt (the GPS-ON trigger)
        │   ├── LocationService.kt          (one fresh fix, then idle)
        │   ├── CopyActivity.kt             (invisible clipboard copy on tap)
        │   ├── ClipboardUtils.kt
        │   ├── ActionReceiver.kt           (DISMISS + 10-min auto-copy)
        │   └── MainActivity.kt             (minimal status screen)
        └── res/...
```

---

## Build & run

### Option A — Android Studio (easiest)
1. Open the `android-gps-copy` folder in Android Studio (Giraffe+).
2. Let it sync Gradle (it will generate the Gradle wrapper automatically).
3. Run on a device/emulator running **Android 10+**.

### Option B — Command line
```bash
cd android-gps-copy
# one-time: generate the wrapper if you don't have one
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

### Option C — GitHub Actions (automatic APK)
1. Push this folder to a GitHub repo.
2. The workflow in `.github/workflows/android.yml` runs on every push.
3. Download the built APK from the run's **Artifacts → app-debug-apk**.

---

## Permissions to grant on the device
- **Location → Allow all the time** (required so a fresh fix can be taken when the
  app is triggered in the background).
- **Notifications** (Android 13+).

## Notes / caveats
- For fully reliable background fixes, "Allow all the time" location is needed; with
  "While using the app" the OS may block the background fetch on some OEMs.
- The 10-minute auto-copy writes to the clipboard from the background. On stock
  Android this works; a few heavily customized OEM ROMs restrict background
  clipboard writes — in that case use the **COPY** button (which always works).
- No analytics, accounts, ads, history, maps, or cloud — by design.
```
