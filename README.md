# MEDIAHUB PRO

**Package name:** `com.mediahub.rebuilt`  
**Status:** Production rebuild in progress (November 2026)

Premium Android media centre with Liquid Glass UI, full video / music / photo support, MediaSession lock-screen controls, and in-app updates from GitHub Releases.

## Goals for this rebuild

- Fix the previous black-screen crash on launch
- Correct MediaStore permission handling (Android 13+)
- Proper MediaSessionService for lock screen + notification controls
- Stable video rendering (picture + sound)
- Background music that continues when the screen is locked
- Liquid Glass floating bottom navigation
- In-app update system via GitHub Releases
- Remote announcements

## Technical stack

- Kotlin
- Android Jetpack Media3 (ExoPlayer)
- MediaSessionService
- minSdk 26 · targetSdk 34 · compileSdk 35
- Package name remains `com.mediahub.rebuilt` (do not change)

## Project structure (being built)

```
app/
  src/main/
    java/com/mediahub/rebuilt/
      ui/          # Screens + Liquid Glass navigation
      player/      # Video + Music players (Media3)
      media/       # MediaRepository, scanners
      service/     # PlaybackService / MediaSessionService
      update/      # GitHub release checker
      util/
    res/
```

## Build instructions

1. Open the project in Android Studio or CodeAssist
2. Sync Gradle
3. Build a debug APK first and test on a real device
4. Only after video + music + permissions work, produce a signed release

**Important:** The original signing key must be used for updates to install over existing installs. Do not change the application ID.

## Current phase

Phase 1 – Foundation (permissions, MediaSession, Liquid Glass nav, crash-free launch)
