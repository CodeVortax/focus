# Gate and watcher

The gate is the soft barrier in front of apps you chose to guard. It is implemented by `WatchService` (detection) and `Overlay` (display).

## Which apps are tracked

Focus logs time for a fixed set of apps (`Config.TRACKED`):

| Package | Label |
|---|---|
| `com.google.android.youtube` | YouTube |
| `com.android.chrome` | Chrome |
| `com.google.android.apps.youtube.music` | YouTube Music |
| `org.videolan.vlc` | VLC |
| `org.telegram.messenger` | Telegram |

All tracked apps are logged. Only the ones switched on under **Setup > Gate these apps** show the overlay. The default gated set is YouTube and Chrome.

## The watcher loop

`WatchService` is a foreground service (type `specialUse`) that runs `step()` once per second:

1. Read new `UsageEvents` since the last event. `ACTIVITY_RESUMED` sets the foreground package; `ACTIVITY_PAUSED` of that package or `SCREEN_NON_INTERACTIVE` clears it.
2. Decide between a **block** overlay and a **gate** overlay (below).
3. Check the study timer alarm and sync its notification.
4. Start or stop the root `TouchCounter` according to the *Precise swipe counting* setting.
5. Refresh widgets. Every 3rd tick polls media sessions for background music; every 5th checks event reminders; every 30th refreshes notifications and "you haven't started" nudges.

On start the service replays the last two hours of usage events so it recovers its state after being killed. `START_STICKY` and the boot receiver restart it.

## Gate overlay

Shown when the foreground app is gated, no overlay is showing, the suppression window has passed, and the app has no active pass.

| Choice | Pass | Notes |
|---|---|---|
| Just search | 10 min | Free |
| A link | 5 min | Free |
| Treat | 10 min | Costs 10 bank minutes; hidden when the bank holds less than 10 |
| Use all | whole bank | Shown when the bank is bigger than 10 |
| I'm distracted | 5 min | Needs a 15 s "Breathe…" wait; logged for the Honesty badge |
| Type a reason | 10 min | Free text becomes the reason stored on the usage row |
| Leave | none | Backs out of the app |

The overlay delays its buttons by 3 seconds normally, or 15 seconds when a not-free block is active. After a pass ends the title changes from **What for?** to **Time's up**. The reason you picked is stored on the usage row and shown in the Log screen.

## Not-free blocks

A `Block` has a name, a start and end minute, a `lock` flag, a `strict` flag and an allow-list.

- **lock = false**: informational. The gate shows the block name and uses the longer delay.
- **lock = true**: only allowed apps work. Any other foreground app gets the **Not-free time** overlay with a **Back to work** button.
- **strict = false** adds an emergency button, *I really need this*, that waits 30 s and grants 2 minutes. Using it five times earns the Emergency Abuser shame badge.
- **strict = true** removes that button entirely.

Always allowed, even during a lock: Focus itself, the launcher, system UI, permission controllers, and anything call-related (`incallui`, `telecom`, `dialer`, `emergency`). A new block with `lock` and an empty allow-list defaults to phone, camera and clock.

Allow-lists accept the keywords `camera`, `phone`, `clock`, `messages`, `browser`, `calculator`, `maps`, a package name, or an app label.

## Leaving an app

`Leaver` backs out of the app instead of pressing Home, because Home makes YouTube enter picture-in-picture. Browsers are the exception: Back walks through tab history, so they go Home. After leaving, the leftovers are killed (`KILL_BACKGROUND_PROCESSES`). A 5 s suppression window stops the gate from re-appearing mid-transition.

## Tuning constants

All in `Config` (`data/Store.kt`):

| Constant | Value |
|---|---|
| `SEARCH_MINUTES` / `LINK_MINUTES` / `CUSTOM_MINUTES` | 10 / 5 / 10 |
| `DISTRACTED_MINUTES` / `DISTRACTED_WAIT` | 5 / 15 s |
| `DELAY_SECONDS` / `BLOCK_DELAY_SECONDS` | 3 / 15 |
| `TREAT_STEP` | 10 |
| `SHORTS_GRACE_MIN` | 2 |
| `REMIND_EVERY_MIN`, `REMIND_FROM_HOUR`, `REMIND_TO_HOUR` | 90, 09:00, 22:00 |
