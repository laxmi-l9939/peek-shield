# Satish's Peek Shield

A lightweight, ad-free privacy shade app for Android — like Infinix "Peek Proof" or BlackBerry "Privacy Shade". It blacks out your screen except for an adjustable clear "peek" window, so only the part you choose is visible to you (and to anyone glancing at your screen).

## Features

- Splash screen: "Made with ❤️ by The Satish" (2 seconds, then fades out)
- System overlay using `SYSTEM_ALERT_WINDOW` ("Draw over other apps")
- Deep black mask that blocks touch input outside the peek area
- Clear peek rectangle that passes touches through to the app underneath
- Drag handle to move the peek area
- Resize handle (bottom-right corner) to resize it
- Opacity slider (0%–100%, including a hard fully-opaque 100%)
- Exit (X) button to remove the shade instantly
- Quick Settings tile to toggle the shade from the notification panel
- Foreground service with a persistent notification so Android won't kill it
- Permission guidance: opens the exact Android settings page for you

## Requirements

- Android Studio (Hedgehog or newer recommended)
- JDK 17 (bundled with recent Android Studio)
- Android device or emulator running **API 24 (Android 7.0)** or higher

## How to build the APK

1. **Open the project**
   - Launch Android Studio → `File` → `Open` → select the `android-peek-shield` folder (the one containing `settings.gradle.kts`).
   - Wait for Gradle sync to finish.

2. **Build a debug APK**
   - Menu: `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.
   - When it finishes, click "locate" in the popup, or find the APK at:
     ```
     app/build/outputs/apk/debug/app-debug.apk
     ```

3. **(Optional) Build a signed release APK**
   - Menu: `Build` → `Generate Signed Bundle / APK` → `APK`.
   - Create or select a keystore, fill in the fields, choose the `release` build type, and finish.
   - Output:
     ```
     app/build/outputs/apk/release/app-release.apk
     ```

4. **Install on a device**
   - Connect your phone with USB debugging enabled, then run from Android Studio (green play button), or:
     ```
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```

## How to use

1. Open **Peek Shield**. The splash shows for 2 seconds.
2. If you haven't granted the overlay permission, the app opens the exact Android settings page. Find "Peek Shield", enable "Permit drawing over other apps", and press back.
3. Tap **Activate Privacy Shade**. The screen goes black except for a clear window in the middle.
4. Drag the handle at the top to move the clear window. Drag the bottom-right handle to resize. Use the slider to change how dark the mask is. Tap the **X** to exit.
5. For instant access later, pull down the Quick Settings panel, tap the edit/pencil icon, and drag the **Peek Shield** tile into your panel. Then you can toggle the shade from anywhere with one tap.

## Project structure

```
android-peek-shield/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/satish/peekshield/
        │   ├── SplashActivity.kt        (splash screen)
        │   ├── MainActivity.kt          (permission flow + activate)
        │   ├── ShadeService.kt           (foreground service + overlay UI)
        │   ├── MaskView.kt               (custom view drawing the black bands)
        │   └── ShadeTileService.kt       (Quick Settings tile)
        └── res/
            ├── layout/activity_splash.xml
            ├── layout/activity_main.xml
            ├── values/{strings,colors,themes}.xml
            ├── drawable/ic_shield.xml
            ├── mipmap-*/ic_launcher*.xml
            └── xml/{backup_rules,data_extraction_rules}.xml
```

## Notes

- The overlay is built from three WindowManager layers: the black mask (consumes touches), a transparent clear view (`FLAG_NOT_TOUCHABLE` so touches pass through to the app below), and a controls layer on top.
- Dragging uses raw screen coordinates so it stays smooth even when the finger moves outside the handle.
- The foreground service uses the `specialUse` type required for `targetSdk 34`.
- No ads, no analytics, no network access.
