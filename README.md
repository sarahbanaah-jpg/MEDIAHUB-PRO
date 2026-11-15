# MEDIAHUB PRO — Native Android Media Player

**Application ID:** `com.mediahub.rebuilt`  
**Language:** Java 11 · Native Android SDK  
**Minimum SDK:** 23 · **Target SDK:** 34 · **Compile SDK:** 35  
**Android Gradle Plugin:** 8.6.1 (JDK 17 and Gradle 8.x required)

## Project package

The complete no-logo source archive is available as **MEDIAHUB_PRO_NO_LOGO.zip** in this ChatGPT conversation. It contains the Android project under the `MEDIAHUB_PRO/` directory. Extract it and open that folder in CodeAssist (the folder containing `settings.gradle`).

## Included app areas

- Home dashboard and separate music library/player screens.
- Video, photo, playlist and equalizer screens.
- MediaStore media discovery, search and sorting UI.
- Storage Access Framework folder selection intended for removable SD-card access.
- Foreground music playback service, notification actions and queue advancement.
- Local library and playlist persistence code.
- Generic music-note launcher icon; no personal mountain image or logo is used.

## Known limitations

This project is a development build, not yet a verified production release. Some UI controls/features may need further implementation and device testing. Advanced video PiP/speed/subtitle/gesture controls, a complete photo-grid/swipe gallery, embedded album-art extraction, notification artwork, reboot playback resumption and full Bluetooth/headset integration are outstanding. SD-card access depends on Android's folder picker and the permissions granted by the user.

## Build verification

A full Android Gradle build and physical-device test have not yet been completed in the available environment because an Android SDK/Gradle toolchain is unavailable here. Import and build in CodeAssist. Share the first build error if one appears so it can be corrected. Do not treat an APK as available until a successful build is confirmed.
