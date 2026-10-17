# Music notification and status-bar capsules

Everything Focus plays, whether the music player or audio from a note, publishes a standard Android **media session** and a **MediaStyle notification** with play/pause, stop and a seek bar. Lock screens read it, and so do the phone makers' status-bar features. None of them has a public API for apps, so Focus does what the system expects and nothing brand-specific.

| Part | Where |
|---|---|
| Music player | `MusicService` (Media3 `MediaSessionService`), channel "Music playback", icon `ic_music_note` |
| Audio from notes | `NoteAudioService`: framework `MediaSession` + `Notification.MediaStyle`, channel "Note audio" |
| Manifest | `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, services declared with `foregroundServiceType="mediaPlayback"` |

Starting one source pauses the other, so there is only ever one active session.

## Make the capsule appear

Settings > **Music notification and capsule** shows the steps for your phone and has buttons for notification and battery settings.

- **Always:** allow Focus's notifications (Android 13+ asks), and set the battery to Unrestricted so the phone does not kill playback.
- **Realme / OPPO / OnePlus:** Settings > realme lab (or Notifications and status bar) > Mini Capsule on, with Music allowed. If it never appears, enable the system app *Ubiquitous Manager Service* (Settings > Apps > App management > Show system apps).
- **Samsung:** lock-screen media and the Now Bar come from One UI; remove Focus from sleeping apps.
- **Xiaomi / Redmi / POCO:** allow the notification, enable Autostart, battery saver No restrictions.
- **Pixel:** Quick Settings and the lock screen show it once notifications are allowed.

## Phones that whitelist music apps

Some Xiaomi, OPPO and other BBK-family phones draw their capsule or "Dynamic Island" only for a built-in list of music apps (QQ Music is the usual one), so a perfectly valid media session is ignored. [Omnibridge](https://github.com/iTaysonLab/Omnibridge) is a separate, free app (GPL-2.0, not bundled with Focus) for this case: it mirrors any player's media session and presents itself as a supported player. Per its README you install it, give it notification access (App info > Allow permissions), and follow its setup. It lists Xiaomi and OPPO as supported; vivo has extra checks, and Realme is not mentioned there but is closely related to OPPO, so it may work. It only needs the player to have a working media session, which Focus has.

## Check it with the test sound

Settings > Music notification and capsule > **Play a test sound** plays a short tone through the same path as note audio. Pull down Quick Settings: Focus should appear in the media player with play/pause. If it does, Focus is doing its part and any remaining gap is the phone's whitelist (see above). If it does not, check notifications and battery first.

Menu names differ between software versions; if yours differs, search the Settings app for "capsule", "Now bar" or "Dynamic Island".
