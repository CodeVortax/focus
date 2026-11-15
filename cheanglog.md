# Version bump

- Focus `0.29.0-beta` (code 47) -> `0.30.0-beta` (code 48): `app/build.gradle.kts`, README badge and line, CHANGELOG heading (the "Unreleased" section became 0.30.0-beta), `fastlane/.../changelogs/48.txt`.
- Study Mate `0.1.3-beta` (code 4) -> `0.2.0-beta` (code 5): `ai/build.gradle.kts`.
- Not changed: `docs/distribution/fdroid-com.barq.focus.yml` still describes 0.29.0-beta. Update it when you tag `v0.30.0-beta`.

# Earlier in this series (batch 2)
Clock-change fix, Quick Settings tile, Study Mate bridge + memory, home layouts. See CHANGELOG.md.
Focus - closing the ways around the Shorts gate + study side (stage 2, includes stage 1)

NOT COMPILED: no Kotlin compiler was available. Build once and send me the first error, if any.
This zip contains everything from stage 1 too: unzip it over the project and replace the files.

SHORTS GATE (stage 1, unchanged)
- One small pass (3 videos) instead of 3/5/10/20/"use all"; a pass runs out after 10 minutes.
- The wait before the choices doubles with each pass (3, 6, 12, 24, 30 s); after 6 passes a day, a typed sentence is needed.
- Looser rules (higher limit, strict mode off) start only after 60 minutes. Tighter rules are instant.
- Overlay permission removed: Focus backs out of the feed. Swipes lost on session restart: fixed. Gate shows on the crossing swipe.
- Stricter id-free pager fallback (YouTube only, portrait, must hold 1.2 s). Treat minutes survive a killed service.
- "Last one in this pass" cue; your own reason shown at the gate (Setup > Gate and Shorts > Your reason).

NEW IN THIS ZIP
1. Phone date/time change no longer resets the day (data/TrustedClock.kt, used by DayClock).
   It follows the real clock only while it agrees with the monotonic clock (2 min). After a restart it takes the wall clock
   again but never earlier than the last time it saw. If you fix a wrong clock by hand, restart the phone once.
2. If the accessibility service is switched off in Android settings (after it was on once), a notification appears after 1 minute.
3. Mistake log (Home menu > Mistake log): subject, question, your answer, right answer, why it went wrong (5 causes),
   how to avoid it. It comes back after 1, 7 and 30 days; "Solved it" moves it on, "Missed again" starts over.
4. End-of-session check: when a subject's timer ends or is stopped, a dialog asks how it went (understood / small review /
   explain the hard part again) with an optional "hardest point". The last two go into the mistake log for review.
5. Arabic for all the new texts (data/StringsV31.kt).

CHANGED / NEW FILES
data/Store.kt, data/I18n.kt, data/TrustedClock.kt (new), data/Mistakes.kt (new), data/StringsV31.kt (new),
service/FocusAccessibility.kt, service/ShortsDetector.kt, service/WatchService.kt, service/Notifier.kt,
ui/SetupScreen.kt, ui/FocusApp.kt, ui/HomeScreen.kt, ui/SubjectScreen.kt, ui/MistakesScreen.kt (new)

NOT DONE YET (needs your decision or more reading of the code)
- 3-task cap, "postpone to tomorrow", exam mode, month/week review screen, plan-ahead slot (they touch the reward and rollover logic).
- Other apps with Shorts-like feeds (Facebook, Snapchat, YouTube clones, browsers): I do not know their view ids.
- Turning the accessibility service off cannot be prevented by an app, only warned about.
