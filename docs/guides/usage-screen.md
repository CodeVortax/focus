# Usage screen

Open **Log** from the home menu, or tap the treat bank card, to get the **Usage** screen: your screen time, in the style of Digital Wellbeing and Nudge, plus Focus's own history.

## Three views

Switch with the chips at the top.

| View | Number | Per-app list |
|---|---|---|
| **Screen time** | Total time on screen | Time per app |
| **Shorts / Reels** | Shorts swiped | Swipes per app |
| **Times opened** | Visits of 3 seconds or more | Opens per app |

## What is on the screen

1. **Big total** for the selected day.
2. **Week chart**: the last 7 days. Tap a bar to select that day; every other section follows it.
3. **Apps**: a donut of the top five apps plus "Other", then the top eight as rows with icon, bar and value.
4. **By hour**: 24 bars for the selected day.
5. **Shorts and playing** for the day: swipes, time, and background media.
6. **History**: the day's visits, each with the times, the reason you gave at the gate, and the Shorts and music bursts nested inside it.

The copy button at the top exports the whole log as text.

## Where the numbers come from

| Source | When | Covers |
|---|---|---|
| **System usage events** | **Usage access** is granted | Every app on the phone, 7 days, rebuilt from foreground and background events |
| **Focus's own log** | Usage access missing, or the system returned nothing | Only the tracked apps (YouTube, Chrome, YouTube Music, VLC, Telegram) |

When it falls back, a line under the chart says so.

How system spans are built (`SysUsage` in `data/Usage.kt`):

- A span opens when an app is resumed and closes when it is paused or the screen turns off.
- Two spans of the same app less than 2 seconds apart are one visit (activity to activity inside one app).
- The launcher, `android` and System UI are skipped, like Digital Wellbeing.
- Days are local midnight to midnight; a span crossing midnight is split.

App names and icons come from the package manager. Apps without a launcher icon may show a name derived from the package.

## Privacy

All of it is computed on the device from data Android already keeps. Nothing is stored beyond Focus's own log, and nothing is sent anywhere. See [Permissions](permissions.md).
