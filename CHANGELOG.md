# Changelog

Versions follow `versionName` in `app/build.gradle.kts`. All releases are betas.

## 0.22.1-beta (version code 35)

Lessons from the closed issues of the Nudge project (astraedus/nudge), checked against Focus.

### Fixed
- **Focus now restarts itself after an app update.** Until now the watch service only started at boot or when you opened the app, so every update left it stopped.
- **"On" no longer hides "dead".** When Android kills the accessibility service it keeps the switch on and never starts it again. If the switch is on but the service has not been running for 90 seconds, Focus posts a notification ("Focus stopped counting Shorts") that opens Accessibility settings; toggling Focus off and on is what brings it back. The notification goes away by itself when the service is running again.

## 0.22.0-beta (version code 34)

### Added
- **Strict Shorts mode** (Settings, next to precise swipes; off by default). When on:
  - the gate opens at once, with no "Just finish this one" and no treat button;
  - the swipe count is hidden;
  - to keep watching you must type a sentence by hand, and you get 5 minutes. Copy, cut and paste are blocked in that box (menu, long-press and large keyboard pastes are all rejected), so you type it every single time;
  - "Close it" backs out until the app is gone and closes it (never Home first, so no picture-in-picture).
- **Optional gray screen** in strict mode: while a feed is open the system colour filter turns the screen gray, and it is restored when you leave. It needs one adb command, once: `adb shell pm grant com.barq.focus android.permission.WRITE_SECURE_SETTINGS`. Without it nothing changes. If gray ever sticks, switch it off in Settings > Accessibility > Color correction.

### Removed
- **Precise swipe counting (root)** and its `getevent` touch reader. The new settle-then-compare counting from 0.21.1 made it unnecessary, so Focus no longer has any root code.

## 0.21.1-beta (version code 33)

### Fixed
- **Shorts / Reels / TikTok swipe counting** (ideas from the SwipeTally test app). Focus no longer counts raw scroll events. It waits until the page settles and counts a swipe only if the video really changed: by the pager's page index when the app reports one, otherwise by comparing a hash of the visible texts (never stored). Fast swipes are no longer lost, small drags that return to the same video no longer count, and comment sheets and carousels are ignored.
- The accessibility service now uses `notificationTimeout=0`, so Android stops merging close scroll events.
- Feed detection gets a structural fallback (a screen-sized vertical pager) that keeps working if YouTube or Instagram rename their views.

## 0.21.0-beta (version code 32)

### Added
- **Release signing**: `gradle assembleRelease` signs with your own key from a git-ignored `keystore.properties`. See [Signing](docs/guides/signing.md).
- **New gate screen** ("What for?"): a calm breathing orb with the countdown, the reasons as two-per-row tiles with an icon and the minutes, a rounded "type why" field with a send button, and a wide Leave button pinned to the bottom. It uses the soft light palette in light mode and your font.
- **Notification player buttons you choose**: Settings > Music notification and capsule lets you pick two extra buttons from shuffle, repeat, favorite (heart), back 10 s, forward 10 s and close. Android shows five buttons in total (previous, play/pause, next and your two).
- **Baloo Bhaijaan 2 SemiBold is now the stock font** everywhere, including the gate (Arabic and Latin). Replace `app/src/main/assets/fonts/default.ttf` to change it. See [Fonts](docs/guides/fonts.md).

## 0.20.2-beta (version code 31)

### Changed
- Light theme is now a darker beige-grey cream (page #D9D2C4, cards #E6E0D3) instead of a pale cream.

## 0.20.1-beta (version code 30)

### Changed
- Light theme is calmer for a low-stimulation look: a soft warm page (#F6F1E7), off-white cards (no pure white anywhere), and accent colours muted toward warm grey. Wallpaper (Material You) colours only tint it and never set the surfaces.

## 0.20.0-beta (version code 29)

### Added
- Music notification and Quick Settings player: **shuffle** and **repeat** (off / all / one) buttons next to previous, play/pause and next, like Samsung's player. They dim when off.

### Changed
- Light theme: a warm cream page with soft white cards instead of stark white, lightly tinted by your accent colour. Top bars and the status area match the page.

## 0.19.2-beta (version code 28)

### Added
- Music: a now-playing strip on every page except Now playing, with the song, a love button, previous, a big play/pause and next. Tap it to open the full player.
- Music: bigger play/pause and love buttons in the full player. Volume and equalizer moved to a row below so the controls no longer run off narrow screens.

## 0.19.1-beta (version code 27)

### Fixed
- Build error in the font code (missing state delegate imports).

## 0.19.0-beta (version code 26)

### Added
- **Sub tasks**: any task can hold small steps (⋮ > Sub tasks). A progress bar shows how many are done; ticking the last one completes the task, unticking one reopens it.
- **Plan a task for any day**: the *This week* option is now *Planned* with a date button, so a task can be due next week or in two months. Change it later from ⋮ > Change date. The reward still shrinks from x2 to x1 as the date approaches.
- **Custom font**: Settings > Appearance > Font lets you pick a .ttf or .otf file to try. To make a font the stock one, put it at `app/src/main/assets/fonts/default.ttf` and rebuild. See [Fonts](docs/guides/fonts.md).

### Fixed
- Light theme colours: surfaces, top bars, containers and text now come from one consistent palette, and the window background follows the app's Light/Dark choice instead of the phone's night mode.

## 0.18.2-beta (version code 25)

### Fixed
- The built-in Music player now shows its media notification, Quick Settings player and capsule. The player service never registered its media session, so Android had nothing to show.

### Added
- [Building Omnibridge](docs/guides/omnibridge-build.md): step-by-step Termux build guide for the optional companion app.

## 0.18.1-beta (version code 24)

### Added
- **Test sound** in Settings > Music notification and capsule: plays a short tone so you can check whether Focus appears in the Quick Settings media player and in your phone's capsule.
- **Omnibridge** guidance for phones that only draw capsules for whitelisted music apps (Xiaomi, OPPO and other BBK phones). See [Music notification](docs/guides/music-notification.md#phones-that-whitelist-music-apps).

## 0.18.0-beta (version code 23)

### Added
- **Music notification and capsules**: audio played from notes now has a real media session and a MediaStyle notification (play/pause, stop, seek bar), and the music player's notification has a proper channel and icon. Lock screens and phone-maker capsules (Realme Mini Capsule, Samsung Now Bar, Xiaomi Dynamic Island, ColorOS and OxygenOS live alerts, Pixel) read it. Settings has a *Music notification and capsule* section with steps for your phone's brand. See [Music notification](docs/guides/music-notification.md).
- **First-run guide**: language, how Focus works, permissions, your subjects, music and notification help. Fresh installs only; replay it from Settings > About. See [First-run guide](docs/guides/first-run.md).
- **Russian, German and French** built in (and chosen automatically on a fresh install when the phone uses one of them).

### Changed
- Starting music stops a playing note recording, and the other way round.

## 0.17.0-beta (version code 22)

### Added
- **Note attachments**: photos, videos, any file (PDF, music...), recordings, web links and links to other notes, all in one list under the note. See [Notes attachments](docs/guides/attachments.md).
- **Arrange mode**: the button at the bottom right of a note turns on drag handles for checklist items and attachments, so both can be reordered by dragging.
- **Mini music player** with a wavy progress line that ripples while playing; tap or drag it to seek.
- **Recording**: Opus voice (about 14 MB an hour at the default), temporary names, a name box when you stop (can be turned off), *Save a copy...* to export with the right extension.
- **Treat series**: pick a folder and the episodes. One episode costs its length from the bank and gives 5 free minutes to seek back; one token, one episode. See [Treat series](docs/guides/treat-series.md).
- **Lock episodes**: renames the files so no other app lists them, with a hidden `.focus-lock.json` in the same folder.
- **Settings** regrouped into collapsible sections; hidden **Developer options** (tap the version 7 times) with *Restore locked episode files*.

### Changed
- Notes database version 5 (new `attach` column); old voice notes move into the attachment list the next time a note is saved.

## 0.16.0-beta (version code 21)

### Added
- **Usage screen**: Digital Wellbeing-style screen time for every app (week chart, donut, per-app list with icons, hourly strip, per-day history), with Screen time, Shorts and Times opened views. See [Usage screen](docs/guides/usage-screen.md).
- **Widgets**: *Streak* and *Shorts* (2x2); *Treat bank* now shows the work streak; *Today* (4x2) now shows a block per subject with its progress. See [Widgets](docs/guides/widgets.md).
- **Card editing**: long-press a card for Edit (title, time, reward, colour), Change media and Delete.

## 0.15.10-beta (version code 20)

### Added
- **TikTok** counting (global and regional builds).
- **Treat bank** and **Today** widgets.

### Fixed
- **Leave in a browser** pressed Back repeatedly, walking through and closing the tab history. Browsers now go Home and nothing is killed. See [Leaving an app](docs/architecture/gate-and-watcher.md#leaving-an-app).

## 0.15.9-beta (version code 19)

### Changed
- **Shorts/Reels detection** now follows Nudge's in-app detector: player container ids first (including Instagram reels opened from a DM or link), then the selected tab, then the original id-fragment walk. See [Shorts detection](docs/architecture/shorts-detection.md).
- **Gate screen** restyled after Nudge: opaque, follows your theme and accent, countdown ring, Leave right under the ring. See [Gate and watcher](docs/architecture/gate-and-watcher.md#the-gate-screen).

## 0.15.0-beta (version code 18)

First public beta: subjects, timers, treat bank, soft gate, not-free blocks, Shorts/Reels counting, notes, PDF reader, music and video player, badges, games, `.foc` files, English and Arabic.
