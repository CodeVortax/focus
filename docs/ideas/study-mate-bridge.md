# Study Mate bridge: ideas

Goal: Focus and Study Mate work together more closely but stay two apps. Focus keeps **no internet permission**; Study Mate keeps the only one. Every idea below keeps the rule already in `DaySummary`: only whole numbers leave Focus automatically; anything with words is something the person chose to share, and sees first.

## Plumbing (do first)
- **Handshake.** Each app declares `<queries>` for the other. A button shows only when the other app is installed; each app works alone. A version number (`bridge v1`) lets old and new builds degrade gracefully.
- **Read-only provider in Focus,** guarded by a *signature* permission (same signing key = same developer). It returns numbers only: tasks done/left, due reviews, days to exam, Shorts minutes, bank minutes, streak.
- **Return path = what already exists.** Study Mate sends a `.foc`-style JSON through the share intent Focus already accepts (Import screen). No new write access to Focus is needed.
- **Bridge log.** A small local list in both apps: what crossed, when, and which direction.

## Ideas
1. **What if you'd tried?** Weekly: Focus makes the numbers (gated-app and Shorts minutes, tasks done, exam pace); Study Mate words them kindly in the person's language. Numbers only.
2. **Quiz becomes reviews.** A Study Mate quiz result is sent back as "↻ review" tasks for the weak topics, using Focus's existing 1/3/7-day spaced review.
3. **Stuck? Ask.** After the gate appears 3+ times for a subject ("hard to get started"), or on a "Stuck" button in the timer: one tap opens Study Mate with the subject. Anything with words is typed or confirmed by the person.
4. **Earn treat time by answering.** 3 questions in Study Mate give a few treat minutes. Focus is the only app that can credit the bank (a call guarded by the signature permission), with a small daily cap so it can't be farmed.
5. **Exam planner.** Focus sends days-to-exam, tasks left and pace; Study Mate proposes a day-by-day plan; accepting it imports tasks through the `.foc` path.
6. **After the timer.** The timer-finished notification gets a "Quiz me" action that opens Study Mate for that subject.
7. **Local-only vs sent.** In Study Mate's preview, mark every field "stays on this phone" or "goes to the model", so the person sees the line between them.

Suggested order: 1, 2, 3, then the provider (needed for 4 and 5).

## Built so far
- My day round trip (Focus confirms, text lands in the message box), the Copy / Ask buttons, memory notes with AI-proposed notes, and a bottom navigation bar in Study Mate.
- Next: "What if you'd tried?" weekly card, quiz results back to Focus as review tasks, a "Stuck? Ask" button after repeated gates.
