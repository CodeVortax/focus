# Gate and watcher

The **watcher** (`WatchService`) learns which app is in front. The **gate** (`Overlay`) is the full-screen "what for?" screen it puts over a gated app. Together they are the soft gate.

## The watcher

`WatchService` is a foreground service of type `specialUse`. It runs one loop, once a second, on the main thread.

| Step | What happens |
|---|---|
| Learn the foreground app | Reads new `UsageEvents`: type 1 (resumed) sets the app, type 2 (paused) clears it if it is the same app, type 16 (screen off) clears it. |
| Warm start | On start it replays the last two hours of events so it knows which app is already in front. |
| Log time | If the app is in `Config.TRACKED`, a row is opened in `usage`. Coming back within 90 seconds reopens the same row, so one visit is one row. |
| Decide the overlay | Block first, then gate (see below). |
| Housekeeping | Timer alerts, the status notification, the Shorts counter, music polling, event reminders, widget refresh. |

Tracked apps: YouTube, Chrome, YouTube Music, VLC and Telegram. All of them are **logged**; only the ones you switch on under **Setup > Gate these apps** get the gate. The default is YouTube and Chrome.

## The gate

The gate is shown when all of these hold:

- the foreground app is in the gated set;
- you are not inside a locked block (blocks take priority);
- no pass is active for that app;
- `Shared.suppressUntil` has passed (it is set while Focus itself is closing an app, so the gate does not pop up during the exit).

Title: **What for?**, or **Time's up** if you already had a pass for this app today.

| Choice | Result |
|---|---|
| **Just search** | 10 minute pass |
| **A link** | 5 minute pass |
| **Treat** | Spend 10 minutes from the bank for a 10 minute pass (only if the bank has 10) |
| **Use all** | Spend the whole bank for a pass of the same length (only if the bank is above 10) |
| **I'm distracted** | A 15 second "Breathe" countdown, then a 5 minute pass |
| **Or type why** | Type a reason, press **Go**, get a 10 minute pass |
| **Leave** | Leaves the app (see [Leaving an app](#leaving-an-app)) |

Every choice writes its reason onto the usage row, so the [Usage screen](../guides/usage-screen.md) can show why you stayed. The choices are locked behind a short countdown: 3 seconds normally, 15 seconds if a not-free block is running.

An empty bank shows **Treat bank empty: finish tasks to earn** as a disabled button.

## The gate screen

The overlay is drawn with plain Android views (a `ScrollView` added through `WindowManager`), not Compose, because a service has no Compose host.

- **Opaque and full-bleed**, so nothing behind it shows through.
- **Follows your theme.** It reads `Store.themeMode` (system, light or dark) and your accent colour. With no accent on Android 12+ it uses the system Material You colours, the same rules as `FocusTheme`.
- **Countdown ring** with the seconds in the middle. The same ring is reused for the "Breathe" wait.
- **Leave sits right under the ring**, large and filled, so it is the easiest thing to press. The other choices are lighter buttons below it.
- Right-to-left follows the app language.
- It takes audio focus while it is up, so whatever was playing behind it pauses.

The look follows [Nudge](https://github.com/astraedus/nudge)'s block screens; the code is Focus's own.

## Not-free blocks

A block is a daily time window (`Block`: name, start, end, lock, strict, allow-list).

- Only blocks with **lock** on enforce anything.
- Inside one, any app that is not **always allowed**, **allow-listed** or **call-related** gets the **Not-free time** screen.
- Always allowed: apps resolved by `Apps.alwaysAllowed` (phone, clock, messages, camera, maps, launcher and so on).
- Without **strict**, there is an emergency button: **I really need this**, a 30 second breathe, then a 2 minute pass. **Strict** removes the button.
- Windows may cross midnight (`22:00` to `06:00`).

## Leaving an app

`Leaver.leave()` is used by every **Leave**, **Back to work** and **Back out** button.

| App | What happens |
|---|---|
| **Browsers** (Chrome, Firefox, Samsung, Edge, Brave, Opera, DuckDuckGo, Vivaldi, Kiwi, Yandex, Tor, any app registered as a browser) | **Home only.** No Back presses and no kill, so your tabs and history stay. |
| Everything else, accessibility on | Press **Back** until the app is gone (up to 8 times, 450 ms apart), then kill leftovers. Falls back to Home if Back does not get out. |
| Everything else, accessibility off | Go Home, then kill after 700 ms. |

Why not Home first for everything: on YouTube it triggers picture-in-picture. Why not Back for browsers: Back walks through, and closes, the tab history.

Browser detection is the fixed list above plus every app that answers the standard `APP_BROWSER` intent (the manifest `<queries>` allow it).

## Reminders from the watcher

Every 90 minutes between 09:00 and 22:00, if tasks are left and no timer is running, it posts "N tasks left on your plate". Turn it off under **Setup > Start reminders**.

## Failure behaviour

- An exception in a step is logged and the loop carries on.
- If the service is killed, open rows are closed with zero length on the next start (`Db.fixOpen`).
- If the overlay cannot be added (no overlay permission), nothing is shown and nothing crashes.
