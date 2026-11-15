# Stillstand 2.0

A minimal Android standby clock with six clock faces, swipe navigation, a custom launcher icon, and automatic landscape/stillness detection.

## What's new
- Swipe left/right on the standby clock to cycle through six faces.
- Tap the clock to dismiss it.
- Blue digital clock is the default face and accent.
- Minimal charcoal/silver launcher icon.
- Tiny "made by Krishn" credit in the app's About area.
- Existing user-enabled foreground service and five-second stillness detection retained.

## Important Android limitation
The standby display is a `TYPE_APPLICATION_OVERLAY` so the service can show it after background sensor detection. Android system bars are controlled by the system and are not reliably hidden by application overlays. The app requests immersive/fullscreen presentation as a best effort, but some Android versions and manufacturers may continue to show the status bar. A regular fullscreen Activity can hide system bars, but Android restricts launching Activities from the background, so replacing the overlay with an Activity would make automatic activation unreliable. This build prioritizes automatic activation.

The overlay appears while the display is on. It does not replace the lock screen or wake a screen that is off. Battery-management behaviour varies by manufacturer.

## Build
Requires JDK 17, Android SDK 35, and Gradle 8.9.

```bash
gradle --no-daemon assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions builds the debug APK on push and uploads it as `Stillstand-debug-apk`.
