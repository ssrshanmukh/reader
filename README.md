# Side-by-side Reader – Android App

Native Android wrapper (WebView) for the Side-by-side Reader PWA.

## What this is

- Complete Gradle project ready for APK builders / Android Studio.
- Loads the original web app from `app/src/main/assets/www/`.
- Preserves all existing functionality of the reader (local storage, import/export zips, gestures, zoom, lock, dark theme, etc.).
- Black OLED theme, edge-to-edge, file chooser support for imports.

## How to build an APK

### Option A – Online APK builders
Upload this entire folder as a ZIP to services that accept Android Gradle projects (e.g. Appetize, some CI services, or dedicated “upload project → get APK” sites).

### Option B – Android Studio
1. Open the project in Android Studio (File → Open → select this folder).
2. Let Gradle sync.
3. Build → Build Bundle(s) / APK(s) → Build APK(s).

### Option C – Command line
```bash
./gradlew assembleRelease
# or for debug:
./gradlew assembleDebug
```
APK will be in `app/build/outputs/apk/`.

## Project structure
```
SideBySideReader/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/www/          ← original PWA files
│       ├── java/.../MainActivity.kt
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/
```

## Notes
- Min SDK 24, Target SDK 34.
- Uses AndroidX + WebKit.
- File System Access API limitations of WebView mean folder-picker behaviour falls back to the in-app “Export / Import zip” flow (same as mobile browsers).
- Icons are taken from the original PWA.
