# Soft Music

Local music player — Kotlin, Jetpack Compose, Media3 1.4.1 (ExoPlayer + MediaSessionService).

## Build APK
**Android Studio:** File > Open this folder, wait for Gradle sync (JDK 17),
then run `gradle assembleRelease` or Build > Build APK(s).
Output: `app/build/outputs/apk/release/SoftMusic-release.apk` (rename to SoftMusic.apk).
The release build is signed with the debug key so it installs directly.

**GitHub Actions:** push to a repo, run "Build APK" -> download artifact SoftMusic.apk.
