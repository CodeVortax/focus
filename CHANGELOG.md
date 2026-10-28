# Changelog

Versions follow `versionName` in `app/build.gradle.kts`. All releases are betas.

## 0.28.0-beta (version code 46)

### Added
- **Spaced review.** After you tick a task, Focus offers to bring it back as a "↻ review" task in 1, 3 and 7 days.
- **Exam countdown.** Subject menu → Exam date…: the subject shows "Exam in 9 days · 4 tasks left · about 0.4 a day", and the Today widget shows the nearest exam.

### Fixed
- **The gate no longer flickers.** It stays on the app until you pick something or press Leave. A passing blip (keyboard, system window, transition) can't hide it; only the app really being gone for a moment can (0.7 s on the home screen, 1.8 s elsewhere). If another gated app comes to the front, the gate moves to it.

## 0.27.0-beta (version code 45)

### Added
- **Study launcher.** A small home screen (clock, running timer, what is left on your plate, only the apps you pick). Make it your home app or open it when you study. Home menu → Study launcher. See [Study launcher](docs/guides/study-launcher.md).
- **Study lock.** For 25 to 120 minutes, or while a subject timer runs, every app that is not on the launcher is blocked (no emergency button) and the phone's settings can't be opened. Ending a time lock early means typing a sentence.
- **Timer widget** (a live countdown of the running subject) and **Piggy bank widget** (saved minutes and days to payout).
- **Menu side.** Settings → Colour and theme → Menu side: the Home menu comes from the left, the right, or follows the language.

### Changed
- **The Home 3-dots menu is a slim side panel** of icons that slides in. Slide it inward or tap the arrow to see the names.
- **One ring.** The timer's own ring is the dial: when no timer runs, draw circles on it (clockwise raises the minutes, one turn = 30). While you draw it shows the turn you are on; when you let go it fills up to a full circle, which is also how the timer starts. The separate dial is gone.

### Fixed
- **Timer ring for budgets over 30 minutes** drains turn by turn from the first second (it used to look frozen until 30 minutes were left); the turns still to come sit underneath as a darker ring.
- **Switching the timer between the small and big card** now animates (the card resizes and fades) instead of jumping.
- **Un-ticking a task takes its reward back.** An accidental tick used to keep the minutes (and the whole-day bonus) even after you un-ticked it. Now the share of the subject's reward, a planned task's reward and the whole-day bonus all go back, and come again when you tick it for real.

## 0.26.1-beta (version code 44)

### Changed
- **Circle dial finishes the ring.** After one turn or more, letting go rounds to the nearest full circle (30, 60, 90 min...), so the ring never ends half done. The +1/+5/+10 buttons still fine-tune.
- **Music pages slide** sideways in the direction of the tab you pick, instead of swapping instantly.
- **Home 3-dots menu is a tile grid** (a bottom sheet) instead of one long list.
- **Settings are split into smaller blocks:** Colour and theme, Check shape, Font and Language are now separate.

### Added
- **Settings → About me:** who made Focus, with an age that works itself out, a GitHub star link, and (empty for now) Buy me a coffee and crypto.

### Fixed
- **Usage time counted time you were away.** Coming back to an app within 90 seconds used to re-open the old row, so the gap counted as time in the app. Every visit is now its own row; the Log still shows close visits as one.
- **Dropped events.** Two system events with the same millisecond (one app pausing as another resumes) could be skipped, leaving the wrong app "in front". Both are handled now.
- **Rows left open by a killed service** now end at the watcher's last heartbeat instead of a zero-length end.

## 0.26.0-beta (version code 43)

### Added
- **Shorts by number.** Settings → Gate and Shorts has a daily Shorts limit. At the Shorts gate you pick how many to watch (3, 5, 10, 20, up to what is left today) instead of "2 minutes". At the limit only a treat or leaving gets you on.
- **Circle dial for minutes.** In a subject, draw circles on the dial: clockwise raises the minutes, one full turn is 30. The budget box is gone; the reward is still typed.
- **Timer sounds live in Settings** (one row per subject). The subject's 3-dots menu has "Timer sound settings", which opens Settings on that block and highlights it.
- **Class days.** Give a subject class days (say Physics on Monday and Tuesday). When one arrives and the plate still has open tasks, the piggy bank pays a funny fine. 4 new badges: Overdue Homework, Deadline Dodger (hidden) and Ready for Class, Always Prepared (46 in total, 15 hidden).
- **Make open tasks last N days.** One button gives every open day task (not the finished ones) a 1, 2, 3, 5 or 7 day limit. Past it the task is late and counts as a miss.
- **Music search button** on the folders, albums, artists and playlists pages and inside any opened list.
- **Music remembers where you were:** the tab, the opened list, its scroll position and the search text.

### Fixed
- **A treat bought at one gate now covers the other.** Before, a treat paid at the app gate could still leave the Shorts gate asking again (and the reverse), which felt like paying and still having no time.
- **History and usage totals.** Visits over midnight count only today's part, and a row left open by a killed service no longer keeps growing (capped at 3 hours, 12 for music).

## 0.25.0-beta (version code 42)

### Added
- **Projects.** Name a project and the apps you will use for it (Chrome, Termux, YouTube...). A session lets those apps skip the gate and spends the treat bank second by second, only while one of them is on screen. Warnings at 5 and 3 minutes left; the session ends when the bank is empty. Home menu → Projects. See [Projects and the piggy bank](docs/guides/projects-and-piggy.md).
- **Piggy bank.** Unused treat time is saved every night and paid into your bank a week later with 10% interest. Days with planned tasks and none done cost a funny fine (speeding ticket, parking ticket, goat tax...) from the piggy. Notifications for payouts and fines. Home menu → Piggy bank.
- **11 new badges** (42 in total, 13 hidden): Piggy Banker, Savings Whale, Payday, Compound Interest, Side Quest, Maker Mode, Project Collector, Squeezed Dry, and the hidden Speeding Ticket, Repeat Offender and Broke Pity.

## 0.24.0-beta (version code 41)

### Added
- **Page transitions.** Screens slide and fade in and out, and Back returns to the page you came from (a real back stack).
- **Each page remembers its scroll position.** Leaving the Games list to play and coming back puts you where you were; the same goes for Home, notes, and the rest.
- **In-app equalizer** for the music player: on/off, the phone's presets, a slider per band and a bass boost. Settings are remembered. The system equalizer is still offered if the player has not started.
- **Notifications and widgets open the right place.** The music notification opens the player; the timer notification and the Today widget's subject blocks open that card; the Streak widget opens Trophies, the Shorts widget opens the log, event reminders open Events.

- **The music player remembers where you stopped.** Open Music and the last queue is loaded, paused on the same song at the same second, ready to play. The position is saved every 10 seconds while playing, when you pause, and on every song change.
- **Treat series: return an episode.** Tap **Return** on an episode you bought but barely watched (up to 20% of it) and all its minutes go back to the treat bank. Past 20% it can't be returned.
- **Treat series: where you stopped.** Each started episode shows a progress bar and "Stopped at 12:30 of 45:00".
- **Music notification buttons.** Settings → Music now lets you choose which buttons the notification shows, with a live preview: previous, next, shuffle, repeat, favourite, back 10 s, forward 10 s, sleep timer (15/30/60 min/off), speed (0.75x to 2x) and close. Play/pause is always there, and Android allows up to four more.

### Changed
- **A bought episode now lasts 1.5 times its length** (it used to be its length plus 5 minutes), so you can seek back and rewatch. Episodes bought before this update keep the old allowance.
- **Game controls and music buttons no longer flip in Arabic.** Left stays left and right stays right in every game, and the player's previous/next, seek bar and transport buttons keep their order.
- **Music Back order.** From any music page, Back goes to the main player, and one more Back leaves the music screen.
- **Less memory in the background.** Cover-art and image caches are released when the app is hidden, and the art cache is halved.

## 0.23.1-beta (version code 40)

### Added
- **Dare Taker badge** (🎲): accept 5 cards from Challenge me (press Start timer in the challenge banner). 31 badges now.

### Changed
- **The app is now called Focus Study Gate** (launcher and accessibility settings). Package name `com.barq.focus` is unchanged, so updating keeps your data.

## 0.23.0-beta (version code 39)

### Added
- **Challenge me.** A button on the home card (next to End day) spins through the subjects that still have open tasks, lands on one and opens it. Inside, it picks a random open task for you (today's tasks first, planned ones only if nothing else is left), shows it in a banner and outlines it in the list. **Another one** picks a different task, **Start timer** starts the subject's timer, **Dismiss** hides the banner. The banner goes away when you tick the task.

## 0.22.4-beta (version code 38)

### Changed
- **The app is now called Barq Focus** (launcher and accessibility settings). The package name `com.barq.focus` is unchanged, so updating keeps all your data. The name makes the app easy to find in stores.
- Store listing: icon, feature graphic, English and Arabic texts and an F-Droid recipe (`fastlane/`, `docs/distribution/`). See [Distribution](docs/guides/distribution.md).

## 0.22.3-beta (version code 37)

Lessons from the closed issues of Nudge and Dechainer, applied to Focus.

### Fixed
- **Floating, pop-up and split-screen windows.** An app opened from an edge panel, Samsung pop-up view or split screen while another app has the focus used to slip past the gate, because Focus only watched one "foreground" app. The gate now also covers any gated app that has a window on screen. This needs the accessibility service to be on.
- **Shorts in a floating window.** Detection, counting and the Shorts gate now read the Shorts app's own window, not just the focused one, and the Shorts gate no longer ends or flickers when another app takes the focus while Shorts is still on screen.
- **Leaving an app that is not the focused window.** Pressing Back would hit the wrong window, so Focus now goes Home and closes the app instead.

### Changed
- The accessibility service now asks Android for the list of windows (`flagRetrieveInteractiveWindows`). Turn the service off and on once after updating if windows are not detected.

## 0.22.2-beta (version code 36)

### Fixed
- **Gate flicker** (the gate disappearing and showing again). An app pausing for a moment, for example during a screen transition, was read as "nothing in front", so the gate was removed and shown again a second later. The gate and the block screen now stay up for a 2.5 s grace when the foreground is momentarily empty. A different app really coming to the front still removes the gate at once. The Shorts gate had the same cause and is fixed the same way.

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
