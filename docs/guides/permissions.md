# Permissions

Focus declares these in `AndroidManifest.xml`. **`INTERNET` is not among them**, so the app cannot open a network connection.

## Special access you grant by hand

| Setting | Manifest / service | Used for | Without it |
|---|---|---|---|
| Usage access | `PACKAGE_USAGE_STATS` | Foreground app detection, per-app time, and the all-app [Usage screen](usage-screen.md) | No gate, no log; the Usage screen shows only tracked apps |
| Display over other apps | `SYSTEM_ALERT_WINDOW` | The gate, block and Shorts overlays | No overlays |
| Accessibility service | `FocusAccessibility` | Shorts/Reels detection in YouTube, Instagram, TikTok; Back-out | No Shorts counting |
| Notification access | `MediaListener` | Reading active media sessions so background music can be logged | Music not logged |

The accessibility service is restricted to four packages (see [Shorts detection](../architecture/shorts-detection.md)). It reads screen structure only to find the feed, counts scrolls, and presses Back when you choose to leave. Nothing is stored except counts and times.

## Normal permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Timer, reminders, status notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | The watcher |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background music and treat playback |
| `RECEIVE_BOOT_COMPLETED` | Restart the watcher after reboot |
| `WAKE_LOCK` | Keep timers and the player reliable |
| `KILL_BACKGROUND_PROCESSES` | Close leftovers when you press Leave |
| `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE` (API 32 and below) | Music library |
| `RECORD_AUDIO` | Voice notes, only while you hold record |

## Package visibility (`<queries>`)

Focus asks Android which apps can handle camera, phone, clock, messages, browser, maps and launcher intents, to build the allow-list keywords for not-free blocks, to recognise browsers (so **Leave** sends them Home instead of pressing Back), and to show app names and icons on the Usage screen. It also checks whether Episteme Reader (`com.aryan.reader`, `com.aryan.reader.oss`) is installed.

## Optional root

*Precise swipe counting* runs `su -c getevent`. It is off by default and does nothing if root is missing.

## Backups

`android:allowBackup="true"` is set, so Android's own device backup may include Focus data if you have it enabled. Disable it in the manifest if that is not what you want.
