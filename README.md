# Stillstand

A premium, minimal Android standby clock inspired by the calm, full-screen feel of phone bedside displays. This is an independent implementation; it does not include Apple's proprietary code, assets, or fonts.

## What it does

- Six full-screen clock faces, including a square analog dial with smoothly sweeping hands.
- Warm ivory, sage, and arctic-blue accent palettes, plus a red low-light face.
- User-controlled background standby detection.
- A foreground service listens to accelerometer and gyroscope signals after the user enables it.
- When the device is held in landscape and remains still for five seconds, a full-screen application overlay fades in.
- Tap the overlay to dismiss it. Movement rearms the detector.
- An ongoing notification exposes a Stop action.

## Important Android behaviour

1. Install and open Stillstand.
2. Tap **Turn on standby**.
3. Android opens **Display over other apps**. Allow Stillstand.
4. Return to Stillstand and tap **Turn on standby** again.
5. You can now return to the home screen. Keep the display on, hold the phone in landscape, and keep it still for five seconds.

The overlay permission is required because Android restricts background activity launches. The app therefore uses a user-enabled foreground service and `TYPE_APPLICATION_OVERLAY`, rather than trying to force-launch an Activity from the background. The service displays a persistent notification while active.

**Limitations:** Android does not guarantee background service survival on every manufacturer build. Some devices aggressively restrict background processes; if detection stops, set Stillstand's battery usage to Unrestricted / Don't optimize in Android settings. The clock appears while the screen is on; it does not replace the lock screen and is not guaranteed to wake a screen that is off. Sensor behaviour also varies by device and case/stand angle. This project has not been device-tested on every Android model.

## Build an APK

The repository includes a GitHub Actions workflow. Push the project to GitHub, open **Actions**, run **Android APK**, then download the `Stillstand-debug-apk` artifact from the completed workflow.

For a local build with JDK 17 and Android SDK installed:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Technical notes

- Kotlin + Android SDK, no third-party runtime dependencies.
- `SYSTEM_ALERT_WINDOW` is granted explicitly by the user in Android Settings.
- Foreground service uses the `specialUse` type with a descriptive subtype because continuous sensor monitoring does not fit the standard media/location/call service types. Play distribution requires policy review and an accurate foreground-service declaration.
- No analytics, ads, network calls, or account sign-in.
