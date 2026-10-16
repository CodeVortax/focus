# Widgets

Focus ships four home-screen widgets. Long-press the home screen, choose **Widgets**, find **Focus**. Tapping any widget opens the app.

| Widget | Size | Shows |
|---|---|---|
| **Focus · Treat bank** | 2x2 | Minutes in the bank and your work-day streak |
| **Focus · Streak** | 2x2 | Your work-day streak, and the light-Shorts streak underneath |
| **Focus · Shorts** | 2x2 | Shorts and Reels swiped today and the time spent on them (turns red above 50) |
| **Focus · Today** | 4x2, resizable | Up to four **subject blocks**, then bank, streak and swipes |

## Subject blocks

On the **Today** widget each block is one study subject, tinted with its card colour:

- the subject name;
- `done/total` for today, or a tick when it is cleared;
- a thin progress bar in the subject's colour.

Subjects with tasks left come first. With no study subjects the widget says **Add a subject with tasks in Focus**.

## Streaks

| Streak | Counts a day when |
|---|---|
| Work streak | You cleared the whole plate, or took the half episode |
| Light-Shorts streak | You swiped 50 Shorts or fewer |

Same numbers as [Badges and XP](badges.md).

## How they update

- Android asks every 30 minutes (`updatePeriodMillis`).
- While the watcher runs, `FocusWidgets.refresh()` redraws all placed widgets at most once every 20 seconds, so a ticked task or a new swipe shows up quickly.
- If you turned battery to **Unrestricted** (see [Setup](setup.md)), the watcher keeps them fresh. Otherwise they update on Android's own schedule.

Colours follow light and dark mode, and your accent colour is used for the big number.

## Source

`service/Widgets.kt`, layouts `res/layout/widget_*.xml`, provider info `res/xml/widget_*_info.xml`.
