# Building

## Requirements

- JDK 17
- Android SDK with platform 34
- Gradle 8.7 (the CI uses this version; there is no Gradle wrapper in the repository)
- `local.properties` containing `sdk.dir=/path/to/Android/Sdk`

## Debug build

```bash
gradle assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Versions

| | |
|---|---|
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 |
| Compose compiler extension | 1.5.14 |
| Compose BOM | 2024.06.00 |
| Media3 | 1.3.1 |
| `compileSdk` / `targetSdk` / `minSdk` | 34 / 34 / 26 |
| Application id | `com.barq.focus` |

Release builds have minification off (`isMinifyEnabled = false`).

## Termux

Add to `~/.gradle/gradle.properties`:

```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

## Continuous integration

`.github/workflows/build.yml` runs on pushes to `main`, pull requests and manual dispatch. It sets up Temurin JDK 17, the Android SDK and Gradle 8.7, runs `gradle assembleDebug --stacktrace`, and uploads `app-debug.apk` as the `focus-debug-apk` artifact.

## Licence headers

Every `.kt` and `.kts` file starts with the AGPL-3.0-only header from [`docs/LICENSE_HEADER.txt`](../LICENSE_HEADER.txt). Add it to new files.
