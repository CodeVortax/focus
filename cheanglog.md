# Batch 2 (includes batch 1)

## Added: Focus
- `BridgeActivity` + `DayExport` + `AiShare`: Study Mate asks, Focus shows the text and returns it only on Send (callingPackage must be com.barq.focus.ai).
- "Copy for AI" / "Ask Study Mate" buttons: Home (`AiShareRow`), subject menu, Usage screen.
- Home layouts: grid (default), list, big, carousel, agenda, rings, mosaic. Chosen with the layout icon on Home; saved in `Store.homeLayout`. `CardTile` is now `internal`.
- Quick Settings tile (study timer).

## Added: Study Mate (ai/)
- `Bridge.kt`, "My day" screen and "+ My day from Focus" chip; text lands in the message box.
- `Memory.kt` (codec, prompt block, REMEMBER suggestions), `MoreScreens.kt` (Memory screen, My day screen, bottom navigation, suggestion card).
- `StudyPrompts.system(..., memory = "")`: memory notes and the REMEMBER rule are added outside care mode only.
- "What is sent" texts updated to mention memory notes.
- New intent action `com.barq.focus.ai.action.CONTEXT` (Focus's "Ask Study Mate").
- `MemoryTest` (10 cases).

## Fixed: Focus (batch 1)
- Clock changes (`ClockGuard`, `WatchService.onClockJump`, `Store.shiftTimer`), watcher revival by the accessibility service, permission-lost notification.

## Not compiled or run
No Android SDK here. Run `gradle :app:testDebugUnitTest :ai:testDebugUnitTest`, then assemble both apps.
Things most likely to need a one-line fix: imports in `HomeLayouts.kt`, the `Store.spent` return type in `DayExport`/`HomeScreen`.

## Decisions to check
- Privacy: the day text contains task titles and app names. It is shown first and sent only when you press Send in Study Mate's chat. Update PRIVACY.md and the Study Mate "what is sent" screen if you ship it. The old numbers-only `DaySummary` is unchanged but still unused.
- Both apps must be installed for the round trip; each works alone.

## Still not built
Bedtime mode, launch-count limits, stopwatch sessions + heatmap, notification batching, custom day start, "What if you'd tried?" card, deep-link actions.
