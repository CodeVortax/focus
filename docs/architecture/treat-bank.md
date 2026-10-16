# Treat bank

The treat bank is a pool of minutes you **earn by finishing work** and **spend on distraction**. It resets every day.

## Earning

| Source | Amount |
|---|---|
| Ticking tasks | A subject's **reward** (default 10 min, editable) is split across its tasks and paid as you tick them. 1 of 4 tasks on a 10 minute subject pays 2.5 minutes. Unticking never takes anything back. |
| Clearing the plate | **+40 minutes** the first time every subject is fully done that day ("the episode"). If you already took the half episode, +20 instead. |
| Half episode | End the day with at least half the tasks done and press **End day**: **+20 minutes**. Once per day, and not after a full clear. |
| Weekly tasks | See below. |

A clear before 18:00 earns the **Speedrunner** badge.

## Weekly tasks

A weekly task is a task you may do any day of the week. It pays **once** (it cannot be paid twice), scaled by how early you finish:

| When you finish | Multiplier |
|---|---|
| Right away | x2 |
| Falling linearly through the week | x2 down to x1 |
| At the deadline (end of the week) | x1 |
| After the deadline | x0.5 |

The default base reward is 15 minutes (`Config.WEEKLY_BASE_MIN`).

## Spending

| Where | Cost |
|---|---|
| App gate: **Treat** | 10 minutes (`Config.TREAT_STEP`) for a 10 minute pass |
| App gate: **Use all** | The whole bank for a pass of the same length |
| Shorts gate: **Treat** / **Use all** | Same |
| Built-in player | Plays treat videos from the bank |

If the bank is under 10 minutes the Treat button is disabled and says **Treat bank empty: finish tasks to earn**.

## Where you see it

- The treat bank card on the home screen (tap it for the [Usage screen](../guides/usage-screen.md)).
- The **Treat bank** and **Today** [widgets](../guides/widgets.md).
- The status notification.

## Storage

Per-day counters in `Store`: `earnedS_<day>` and `spentS_<day>` in seconds, plus `subs_<day>_<cardId>` for what each subject has already paid. `bank = earned - spent`, never below zero. Whole minutes are what the gate can sell; the display shows tenths.

## Why it is built this way

Paying per task (not per subject) means a half-finished subject still moves the bank, so there is always a reason to tick one more. The bank resets daily so you cannot save a week of study for a night of scrolling.
