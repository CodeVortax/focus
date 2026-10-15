# Privacy policy

**Focus keeps everything on your phone.** The app declares no `INTERNET` permission, so it has no way to send data anywhere. There are no accounts, analytics, telemetry, crash reporting, ads or third-party SDKs.

You can verify this: read `app/src/main/AndroidManifest.xml` and `app/build.gradle.kts`, and read every line of the source (AGPL-3.0-only).

## What Focus stores, and where

| Data | Where | Purpose |
|---|---|---|
| Subjects, tasks, timers, treat bank, settings, gated apps, blocks, streak counters | App-private preferences | The app itself |
| Usage log: which tracked app was in front, when, for how long, the reason you gave, Shorts swipe counts | App-private SQLite (`focus.db`) | Log screen, badges, treat-bank rules |
| Notes, labels, checklists | App-private SQLite (`notes.db`) | Notes |
| Voice recordings | App-private `voice/` folder | Voice notes |
| Events and meetings | App-private preferences | Reminders |
| Language packs you import | App-private preferences | Translation |
| PDFs, videos, audio you open | **Not copied.** Focus keeps a read permission on the file you picked | Library and player |

## What the special permissions can see

- **Usage access:** the package name of the foreground app and when it changed. Used to log time and show the gate.
- **Accessibility service:** limited to YouTube, Instagram and TikTok. Focus inspects the screen structure of those apps to decide whether a short-form feed is open, counts scroll events, and presses Back when you choose to leave. It does not read messages, text you type, passwords or notification content, and it stores only counts and times.
- **Notification access:** used only to list active media sessions (which app is playing audio). Focus does not read the content of notifications.
- **Microphone:** only while you record a voice note.
- **Audio library:** read by the music player.
- **Root (optional):** if you enable *Precise swipe counting* and have root, Focus reads raw touchscreen events with `getevent` to count swipes. Coordinates are not stored.

## Sharing and export

Nothing leaves the phone unless **you** do it: sharing a notes backup, exporting a `.foc` file, copying a crash log or captured screen IDs. Those go wherever you send them.

## Device backup

The manifest sets `allowBackup="true"`, so if you use Android's system backup, Focus's private data may be included in it. That is controlled by your device and Google account settings, not by Focus.

## Deleting your data

Uninstalling Focus removes all of its private data. You can also clear it from Android **Settings > Apps > Focus > Storage > Clear data**.

## Children

Focus does not collect personal information from anyone, including children.

## Changes

If this policy changes, the change appears in this file and in the version history of the repository.

Arabic version: [PRIVACY.ar.md](PRIVACY.ar.md)
