# Building Omnibridge in Termux

[Omnibridge](https://github.com/iTaysonLab/Omnibridge) (GPL-2.0) is a separate app. It mirrors a media session from a player such as Focus and presents it as a music app your phone's capsule trusts. It has no releases, so you build it yourself. Focus does not bundle it.

## How it works
Omnibridge has five build flavors that each install under the package name of a music app that phone makers whitelist:

| Flavor | Package name |
|---|---|
| asNetease | com.netease.cloudmusic |
| asViper | com.kugou.viper |
| asQQ | com.tencent.qqmusic |
| asLuna | com.luna.music |
| asHiby | com.hiby.music |

Install only **one** flavor, and uninstall the real app of that name first. It needs notification access.

## Build in Termux
Omnibridge ships its own Gradle wrapper, so use `./gradlew` from inside the cloned folder (not `gradle`).

```sh
cd ~/Omnibridge
```

1. **SDK path.** Reuse the one Focus uses:
   ```sh
   cp ~/focus/local.properties .
   ```
   It must contain `sdk.dir=/path/to/Android/Sdk`.
2. **aapt2.** Google's aapt2 is x86 and fails on a phone, so point Gradle at Termux's. Install it and add the override:
   ```sh
   pkg install aapt2
   mkdir -p ~/.gradle
   echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2" >> ~/.gradle/gradle.properties
   ```
   (If you already did this for Focus, it is already set.)
3. **Platform 36.** Omnibridge compiles against Android 36, Focus only needs 34:
   ```sh
   ls "$(grep sdk.dir local.properties | cut -d= -f2)/platforms"
   ```
   If `android-36` is missing, install it with `sdkmanager "platforms;android-36" "build-tools;36.0.0"`.
4. **Build one flavor:**
   ```sh
   ./gradlew assembleAsNeteaseDebug
   cp app/build/outputs/apk/asNetease/debug/*.apk ~/storage/downloads/
   ```
   The first run downloads Gradle and takes a while. The debug APK is signed automatically, so it installs as is.

### If Termux's aapt2 is too old
Termux's aapt2 can be older than what Android 36 resources need. If you see an aapt2 error mentioning an unsupported version or resource, build it on GitHub instead:

1. Fork [Omnibridge](https://github.com/iTaysonLab/Omnibridge) on GitHub.
2. Add `.github/workflows/build.yml`:
   ```yaml
   name: build
   on: workflow_dispatch
   jobs:
     apk:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with: { distribution: temurin, java-version: 17 }
         - uses: android-actions/setup-android@v3
         - run: ./gradlew assembleAsNeteaseDebug
         - uses: actions/upload-artifact@v4
           with: { name: omnibridge-netease, path: app/build/outputs/apk/asNetease/debug/*.apk }
   ```
3. Run it from the Actions tab and download the APK artifact.

## Use
1. Uninstall the real app with the same package name if it is installed.
2. Install the APK, open Omnibridge, grant **Notification access** (App info > Allow restricted settings first if the toggle is greyed).
3. Play something in Focus. Omnibridge mirrors the session and the capsule should appear.
4. Use the Test sound button in Focus Settings to check.

If the capsule still does not appear, your phone may check more than the package name (vivo does). That is outside Focus's control.
