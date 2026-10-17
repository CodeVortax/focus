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

## Build
Requirements: JDK 17 (Android Gradle needs 17+, the project targets Java 11), compileSdk 36, minSdk 29.

```sh
pkg install git openjdk-17
git clone https://github.com/iTaysonLab/Omnibridge
cd Omnibridge
```

Termux's own `aapt2` is needed because the Google one is x86. Install the SDK tools (for example with the `android-sdk` package or the `termux-gradle` helpers you already use for Focus), then point Gradle at them in `~/.gradle/gradle.properties`:

```
android.aapt2FromMavenOverride=/path/to/termux/aapt2
```

Build one flavor (pick the one your phone whitelists; Netease is a common choice for ColorOS/Realme and Xiaomi):

```sh
./gradlew assembleAsNeteaseDebug
```

The APK is in `app/build/outputs/apk/asNetease/debug/`. The debug build is signed automatically, so it installs as is:

```sh
cp app/build/outputs/apk/asNetease/debug/*.apk ~/storage/downloads/
```

## Use
1. Uninstall the real app with the same package name if it is installed.
2. Install the APK, open Omnibridge, grant **Notification access** (App info > Allow restricted settings first if the toggle is greyed).
3. Play something in Focus. Omnibridge mirrors the session and the capsule should appear.
4. Use the Test sound button in Focus Settings to check.

If the capsule still does not appear, your phone may check more than the package name (vivo does). That is outside Focus's control.
