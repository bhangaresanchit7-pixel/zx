# ZX Launcher

Ultra-light black launcher for low-end devices and emulators.

- Android 4.0+ (API 14 and up)
- No dependencies, no support library, no layout XML (UI built in code)
- Black theme, auto-adjusting grid: more columns in landscape, fewer in portrait
- Handles rotation itself (no Activity restart, saves RAM and CPU)
- Lazy icon loading with a small 48-icon cache, cleared on low memory
- Auto-refresh when apps are installed or removed
- Long-press an app to open its App Info (uninstall from there)
- Focusable grid, so keyboard/D-pad navigation works in emulators

## Build

1. Install Android Studio (or the Android SDK + Gradle 7.5).
2. Open the `ZX` folder, let it sync, then Build > Build APK.
   Or from a terminal: `gradle assembleRelease`
3. APK ends up in `app/build/outputs/apk/release/`.

For Android 4.0 devices/emulators, install the APK and press Home, then choose ZX as the default.

## Build without installing anything (GitHub)

1. Create a new GitHub repo and upload this whole folder (keep the .github folder).
2. Open the Actions tab, run "Build ZX APK" (it also runs on every push).
3. When it finishes, download the `ZX-apk` artifact: that zip contains the APK.

## Optimize button

Closes background apps, frees RAM, clears ZX's own cache and asks Android to free app caches.
System-wide cache cleaning only works on Android 4.x to 6; newer versions block it for normal apps.
