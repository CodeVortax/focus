# Treat bank

The treat bank is a daily balance of minutes you earn by finishing tasks and spend on distraction. It lives in `Store` under day-keyed counters (`earnedS_<day>`, `spentS_<day>`), so each new day starts at zero.

```
bank (seconds) = max(0, earned today - spent today)
```

## Earning

### Subject reward, split across tasks

Every study subject has a **reward** (default 10 min) for finishing the whole subject. It is paid proportionally:

```
target = reward x 60 x (tasks done) / (tasks total)
```

Finish 1 of 4 tasks in a 10-minute subject and you hold 2.5 minutes. `settle()` only ever adds: unticking a task never takes anything back, and each subject remembers what it has already paid today.

### Day bonuses

| Bonus | Minutes | When |
|---|---|---|
| Episode bonus | +40 | Every task of every subject is done |
| Half-episode | +20 | You press **End day** with at least half the tasks done (and the full bonus is not already claimed) |

If you cash the half-episode and later finish everything, the full bonus is reduced by the 20 you already took. Finishing the plate before 18:00 flags a speedrun for the Speedrunner badge.

### Weekly tasks

Weekly tasks can be done any day. They carry a base reward (default 15 min) multiplied by how early you finish:

| When | Multiplier |
|---|---|
| The moment you add it | x2 |
| Sliding linearly toward the end of the week | down to x1 |
| After the deadline | x0.5 |

The reward is paid once (`paid` flag), so ticking and unticking cannot be farmed.

## Spending

| Where | Cost |
|---|---|
| Gate overlay: *Treat* | 10 min per purchase (`TREAT_STEP`) |
| Gate overlay: *Use all* | The whole bank |
| Shorts/Reels overlay: *Treat*, *Use all* | Same |
| Built-in player (treat cards with a video or audio file) | One second of bank per second of playback; pauses when the bank is empty |

When the bank holds less than 10 minutes the button is replaced with a disabled "Treat bank empty: finish tasks to earn".

## Daily rollover

On the first launch of a new day `rollover()` removes finished tasks and keeps unfinished ones, so your plate carries over while your bank does not. The biggest plate you had that day is remembered for the Plate Hoarder badge.

## Widgets

The **Treat bank** widget shows the current balance. The **Today** widget (4x2, resizable) shows tasks done, the bank and Shorts swiped today. Tapping either opens Focus.
