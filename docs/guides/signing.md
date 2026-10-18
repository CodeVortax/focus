# Signing the APK

A release APK is signed with a key only you hold, so every update installs over the previous one. **Keep the keystore file and its passwords safe and backed up: lose them and you can never update the installed app, only reinstall it.** Never commit them (they are git-ignored).

## 1. Make a key (once)
In Termux:

```sh
pkg install openjdk-17
keytool -genkeypair -v -keystore ~/focus-release.jks -alias focus -keyalg RSA -keysize 4096 -validity 36500
```

Choose a strong password. Back up `~/focus-release.jks` somewhere safe (not only on the phone).

## 2. Tell Gradle about it
Create `~/focus/keystore.properties`:

```properties
storeFile=/data/data/com.termux/files/home/focus-release.jks
storePassword=YOUR_PASSWORD
keyAlias=focus
keyPassword=YOUR_PASSWORD
```

## 3. Build the signed APK
```sh
cd ~/focus
gradle assembleRelease
cp app/build/outputs/apk/release/app-release.apk /sdcard/Download/focus-signed.apk
```

Check the signature:

```sh
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties` the release build is left unsigned, so it will not install.

## Switching from the debug-signed app
Android refuses to update an app signed with a different key. Before the first signed install: export your notes and data (Import / Export), uninstall the debug-signed Focus, install the signed APK, then import.
