# Architecture overview

Focus is a single-module Android app (`:app`, package `com.barq.focus`) written in Kotlin with Jetpack Compose. It has no backend and no network access.

## Layers

```
ui/        Compose screens. Read and write through Store / Db / NotesDb.
service/   Long-lived pieces: the watcher, the overlay, the accessibility service,
           reminders, alerts, widgets, the music service.
data/      Persistence, rules and formats: Store, Db, NotesDb, Foc, I18n, Game, Events, Music, Apps.
```

`ui/` never talks to Android system services for monitoring; `service/` does. They meet in `data/`.

## Runtime components

| Component | Type | Role |
|---|---|---|
| `MainActivity` | Activity (`singleTask`) | Hosts `FocusApp`; receives `.foc`/`.fcs` files and shared text |
| `WatchService` | Foreground service (`specialUse`) | Polls foreground app every second, logs usage, shows gate and block overlays |
| `FocusAccessibility` | Accessibility service | Scans only YouTube, Instagram and TikTok for short-form feeds |
| `MediaListener` | Notification listener | Exists only so Android lets Focus read active media sessions |
| `MusicService` | Media session service | Background playback for the built-in music player |
| `TimerReceiver`, `ReminderReceiver` | Broadcast receivers | Timer alarms, event and nudge reminders |
| `TreatWidget`, `TodayWidget` | App widgets | Home-screen treat bank and today summary |
| `BootReceiver` | Broadcast receiver | Restarts the watcher after reboot |

## Navigation

`FocusApp` is a small state machine over a `screen` string: `home`, `subject`, `timer`, `notes`, `note`, `player`, `pdfs`, `pdf`, `import`, `music`, `games`, `trophies`, `log`, `schedule`, `events`, `setup`. Back always returns to a sensible parent (`note` to `notes`, `pdf` to `pdfs`, everything else to `home`). The active timer survives process death: `FocusApp` reopens the card whose id is stored as `activeCardId`.

## Storage

| What | Where |
|---|---|
| Settings, cards, tasks, blocks, gated apps, treat bank, daily counters, language packs | `Store` (SharedPreferences) |
| Usage log (`app`, `music`, `shorts` rows with start, end, reason, swipe count) | `Db`, SQLite file `focus.db`, table `usage` |
| Notes | `NotesDb`, SQLite file `notes.db` |
| Events and meetings | SharedPreferences file `focus_events` |
| Voice notes | Private `voice/` folder, AAC `.m4a` |
| Imported PDFs and treat media | Referenced by URI with a persisted read grant; the files are not copied |

Nothing is uploaded anywhere. See the [privacy policy](../../PRIVACY.md).

## Data flow for one gated visit

1. `WatchService.step()` reads new `UsageEvents` and updates the foreground package.
2. If the app is tracked, a row is opened in `usage`. Returning within 90 s merges into the same row.
3. If the app is in the gated set and no pass is active, `Overlay` is shown with the available choices.
4. A choice calls `grant(pkg, reason, minutes)`, which records the reason on the row and starts a pass. Treat choices also call `store.spend(...)`.
5. When the pass expires the next tick shows the gate again with the title "Time's up".

Details: [Gate and watcher](gate-and-watcher.md).

## Design rules

- **Fail open.** The detector and the watcher swallow exceptions; a failed scan means "not Shorts", never a crash.
- **Cheap first.** The Shorts detector tries direct id lookups before any tree walk, and the walk has a node budget.
- **Local only.** No dependency pulls in networking.
- **One header, one licence.** Every source file carries the AGPL-3.0-only header in [`LICENSE_HEADER.txt`](../LICENSE_HEADER.txt).
