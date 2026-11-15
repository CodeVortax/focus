# Fix Focus: Shorts logic

Based on reading `focus-main (2).zip`: `ShortsDetector.kt`, `FocusAccessibility.kt`, `Store.kt`, `Db.kt`.
Nothing here was run on a phone. Part A is code suspects (unconfirmed until tested). Part B is design ideas about how the gate affects your head, not new features.

---

## Part A: Code suspects (in order of likelihood)

### A1. A swipe is lost when a session restarts
- **Where:** `FocusAccessibility.onAccessibilityEvent`, `startShorts`
- **Problem:** Swipes are only counted when `inShorts == true`. The session ends if `lastFoundAt` is not refreshed for 4 s (paused video, few events). The next scroll event restarts the session in `scan()`, after the count branch has already been skipped. `startShorts` also skips its "+1" if you return within 90 s.
- **Effect:** The counter is lower than the real number. The gate comes late.
- **Fix:**
  - On a scroll event from a Shorts package while `!inShorts`, run `scan()` first, then count if it started a session.
  - Raise the 4 s timeout (about 10 s), or refresh `lastFoundAt` on any event from the package while it is in front and was detected recently.

### A2. `isPager` runs every time and can give false positives
- **Where:** `scan()`: `isShorts(...) || isPager(...)`
- **Problem:** Any large vertical scroller with 6 or fewer children, where one child fills 80% of the height, counts as "Shorts". Instagram's normal feed posts and some YouTube screens can match. It also depends on orientation (`sw`/`sh`).
- **Effect:** The gate appears on normal content, and swipes there get counted.
- **Fix:**
  - Use `isPager` only when the id checks fail, and only for a short window after a known Shorts or Reels entry.
  - Skip it in landscape.
  - Use the debug flag (`store.debug`, `debugIds`) to see what actually matches in the Instagram feed.

### A3. The gate appears one video late
- **Where:** `shortsGateDue`: `swipes > allow`
- **Problem:** After "Watch 3 more" you see 3 videos, and the gate appears on the 4th. The 4th plays for about 0.25 to 1.25 s (250 ms settle plus a tick every second) before the gate covers it.
- **Effect:** You get a "peek" at one extra video, with sound.
- **Fix:**
  - Pause or cover as soon as the swipe that crosses the limit is detected, not on the next tick (call the gate from `settle`).
  - Or use `>=` and set the allowance so it shows at the boundary.

### A4. Time tracking stops when the screen is quiet
- **Where:** the 4 s timeout, `endShorts`
- **Problem:** The usage row closes, and grayscale (`Gray`) turns off, whenever nothing happens for 4 s.
- **Effect:** "Minutes today" is lower than the real time, and the gray effect flickers.
- **Fix:** Same as A1. Also merge rows that are less than 90 s apart when computing minutes.

### A5. A treat can be lost
- **Where:** `showShortsOverlay`: `store.spend(...)` then `Shared.treatUntil = ...`
- **Problem:** `treatUntil` lives only in memory. The minutes leave the bank at once. If Android kills the service, the minutes are gone.
- **Fix:** Save `treatUntil` in `Store` (SharedPreferences) and read it from there.

### A6. Small things worth checking
- `Gray.recover` and `Gray.off`: make sure grayscale is always restored if the service dies.
- `shortsAllow()` is stored per day, so it resets at the day boundary. Check that `day()` uses `dayStartHour` (custom day start) the same way everywhere, so the swipe count and the allowance reset at the same moment.
- `sideways()` works only on API 28+. On older phones, carousels may count as swipes.

---

## Part B: Mental logic (how the gate affects behavior)

The code works like a vending machine: you ask, it gives, and you have a bank to pay with. For a person with attention problems that is weak, because it relies on a decision at the moment when decisions are hardest. These are the changes I would make, in order of how much I think they matter.

### B1. Fewer choices, smaller numbers
- **Now:** The gate offers 3, 5, 10 and 20 more videos, plus Treat and Use all.
- **Problem:** Each choice is a decision, and the big numbers invite bargaining ("just 20"). Using the whole bank at once is the opposite of a limit.
- **Change:**
  - Offer at most two options: "3 more" and "Back out". "Back out" is the biggest, easiest button.
  - Remove "Watch 20 more" and "Use all". If someone needs more, they can come back through the gate again.

### B2. Friction that grows
- **Now:** `Config.DELAY_SECONDS` is the same every time.
- **Change:** Make the wait grow each time the gate is passed in one day (for example 3 s, then 6 s, then 12 s). The first pass stays easy, so it doesn't feel like a punishment. Later passes get harder, which is where the habit is.
- **Note:** A short pause helps because it breaks the automatic reflex. It stops helping if it becomes irritating, so keep the first one short.

### B3. Show the cost in your own terms
- **Now:** "12 of 30 today · 14 min".
- **Change:** Show time next to something the user cares about, such as "14 min = half of a study session" or "that is your planned break, used". Real numbers about time are easier to feel than counts of videos.
- **Why:** Time blindness is common in attention problems. A number of videos does not tell you how long you've been there.

### B4. Make the end visible
- **Problem:** The talk (Tyler Burns) points at the infinite feed as the issue. Stopping needs a clear ending.
- **Change:** When the allowance is nearly used (for example 1 left), show a small "last one" cue (the Pill), so the end comes as a known point and not a sudden wall.

### B5. Don't make Shorts the prize
- **Now:** Finishing tasks earns treat minutes, which are spent on Shorts.
- **Problem:** That teaches "work, then reward = scrolling", and it makes Shorts the thing you work for.
- **Change:** Let the bank also buy other things (a game from the app, music, a real break with a timer). Keep Shorts as one option, not the only one.

### B6. Ask for one sentence of intention before the allowance
- **Change:** Before granting "3 more", ask a very short question: "What are you looking for?" with two taps (e.g. "A break" / "I don't know"). If it is "I don't know", show "Back out" more strongly.
- **Why:** Naming a purpose is a light version of the "choose, don't be fed" idea from the talk. Keep it one tap, or it becomes a chore and the user will start tapping anything.

### B7. A one-tap check-in after leaving
- **Change:** After a session of 5 min or more, show a very small "How do you feel?" (better / same / worse). Store it. After a week, show: "On days with more than 20 Shorts you felt worse N times."
- **Why:** It lets the person see their own pattern, which works better than being told.

### B8. Tone: no shame
- The talk's main message is "it is not your fault". The gate text should say the same.
- Check `Support.kt` and `Moment.GATE_EMPTY` / `GATE_EARNED`. Avoid wording like "you failed" or "bank empty, go work". Prefer "You have 2 left. Want to stop here?"

### B9. One shared limit across apps
- The daily counter is already shared (`shortsSwipes` is not per app), which is good. Keep it that way so moving from YouTube to Reels does not reset anything. Tell the user so in the gate ("across all apps").

### B10. Protect the vulnerable hours
- Let the user set a "quiet window" (for example study hours or after midnight) where the allowance is smaller or strict mode turns on by itself. Stopping is hardest when tired, so the setting should be decided in advance, when the user is calm.

---

## Part C: How to know it works

Do not trust intuition. Log these per day (they are mostly in `usage` already):
- gates shown
- times "Back out" was chosen vs "Watch more"
- total Shorts minutes
- check-in answer (if B7 is added)

The number that matters most is the back-out rate. If it goes up over two weeks, the gate is working. If it stays flat, change one thing at a time (B1 first), not everything together.

These ideas come from general behavior-change thinking (friction, reducing choices, visible costs) plus the talk. They are not proven for you in particular, which is why the test above matters.

---

## Part D: Ideas from the four videos (what to use, what to skip)

Sources: Tyler Burns (TEDxBasel, scrolling), "Get your brain addicted to hard things" (dopamine, friction, quit zone), "Bad memory" (memory handling), "How to get whatever you want" (asking clearly).

How much to trust each one:
- **Memory video:** the strongest. Active processing when you learn, and retrieval practice, are well supported. The exact "15 to 30 seconds" is a simplification, so treat it as "do it right away", not as a precise number.
- **Hard things video:** useful ideas (friction, anticipation, the quit zone, "why" three times), but "dopamine detox/reset" is popular simplified science. Use the behavior parts, ignore the brain-chemistry claims.
- **Get whatever you want:** the useful part is "be specific about what you want". The "believe like a child" part is motivation talk, not something to build into an app.
- **Tyler Burns:** the design principle is to choose what you consume instead of being fed, and "it is not your fault".

### D1. Show the user's own "why" at the gate (high value, small change)
From the "why three times" idea. When a subject or goal is created, ask once: "Why do you want this?" (one sentence). At the Shorts gate, show that sentence under the buttons.
- The reason comes from the user, so it works better than a generic message.
- It also checks whether the goal is really theirs. A goal that feels dead gets harder to follow (the "wrong wall" idea).

### D2. Bridge the quit zone
Habits are most likely to fail between about 1 month and 1 year, because the reward is far away.
- Show a weekly or monthly trend (study minutes, Shorts per day going down), not only today's numbers.
- Mark small milestones at week 2, week 4 and week 8.
- Tie the treat bank to the long goal: "12 tasks this week toward [your goal]".

### D3. Something to look forward to
Planned fun gives motivation without needing a feed.
- In the day plan, add one optional slot: "What are you looking forward to today?"
- Show it in the gate: "Tonight: [that thing]". Keep it small. One slot, not a new screen.

### D4. Friction on both sides
- **More friction on Shorts** (already started): gray mode, the growing wait from B2.
- **Less friction on studying:** one tap from the home screen to start the timer on the hardest task of the day. A fixed first block ("hardest thing first") is easier than deciding each time.

### D5. Offer a zero option, not only a limit
"99% is easier than 100%" applies here: a count limit is moderation, and moderation needs a decision every time.
- Add a "Shorts-free for N days" option (for example 7 or 30). It can reuse strict mode.
- Do not force it. Let the user choose, and say plainly that some people find zero easier than a limit.

### D6. Study side: handle it right away (from the memory video)
For notes, the PDF reader and the AI study part:
- **Pause for handling:** after a page or a section, show a light prompt: "Say this in one sentence." Do not let the next page load until the user taps. Optional, not forced.
- **Retrieval before reward:** at the end of a study session, ask for 1 minute of recall ("write what you remember"). Credit the treat bank after that, not only for the time spent.
- **Retrieval practice from the user's own notes:** make the questions from the notes (the AI part can do this), and bring them back after 1 day, 1 week and 1 month. Avoid cards that only test the exact wording.
- **Think on paper:** let the user draw connections between notes (a small map of linked notes). This matches Tyler Burns's idea: shapes and maps are easier for an attention-limited brain than long lists.
- **Quiet:** in study mode, offer to silence notifications and suggest headphones.

### D7. Specific tasks (from "ask clearly")
- A task should carry what, how much and by when. Add a one-line hint when the title is vague ("Study" becomes "Chapter 3, 20 questions, by 8 pm").
- Optional "When X happens, I will do Y" line for habits.

### How to avoid turning this into a feature pile
You said you want the app to work mentally, not just have features. Pick a few and stop:
1. D1 (own "why" at the gate)
2. B1 and B2 (fewer choices, growing wait)
3. D6 "retrieval before reward" (short recall at the end of study)
4. D5 (zero option)

Everything else can wait until the first four have been used for two weeks. Test with the numbers from Part C.

---

## Part E: Ways around the gate found in the code (status)

| Way around | Status |
|---|---|
| "Watch 20 more" and "Use all" | Done: one small pass of 3 videos |
| A pass lasting all day | Done: a pass runs out after 10 min |
| Repeating passes | Done: the wait doubles; after 6 passes a typed sentence is needed |
| Raising the limit or turning strict mode off in Settings | Done: looser rules start after 60 min |
| Taking away the overlay permission | Done: Focus backs out of the feed |
| Swipes lost when a session ends; gate a video late | Done |
| False triggers in the normal feed | Done (stricter fallback) |
| Changing the phone date or time | Done: trusted clock (restart once after fixing the clock by hand) |
| Switching the accessibility service off | Warned after 1 min (an app cannot prevent it) |
| Other apps with Shorts-like feeds | Open: view ids unknown |

Done from Part B and D: B1, B2, B4, D1, mistake log (D6), end-of-session check (D6), Arabic texts.
Not done yet: 3-task cap, postpone, exam mode, month/week review screen, B3, B5-B7, B9, B10, D2, D3, D5.

---

## Suggested order
1. A1, A2, A3 (they make the counting and detection correct; without that, the psychology changes have nothing to stand on)
2. B1, B2, B3 (smallest code change, biggest effect on behavior)
3. A5, B4, B8
4. B5, B6, B7, B10
5. Part D, starting with the four picks at the end of that part
