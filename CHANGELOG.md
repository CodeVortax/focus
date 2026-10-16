# Changelog

Versions follow `versionName` in `app/build.gradle.kts`. All releases are betas.

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
