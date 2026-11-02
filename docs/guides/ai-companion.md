# AI study mate (experimental, branch `companion-ai`)

## Why this exists, honestly

I'm a student too. I built Focus because I get distracted and I scroll more than I want to, and I think a calm, kind voice that notices how my day is going and helps me take one small step could actually help me, and maybe other students.

I'm not sure it will work. It might be annoying, or wrong, or not worth the privacy cost. That is why it lives on its own branch, is **off by default**, and ships only if it proves useful and safe.

## What it is not

- Not a doctor, therapist or teacher, and not a replacement for any of them.
- Not in charge. The app decides when it may speak and what it may do. The AI only puts an already-allowed moment into words.
- Not able to change your limits, blocks, passes or the gate. No setting, chat message or clever sentence can make it do that, because that code path does not exist.
- Not always right. It can say wrong or unhelpful things. Ignore it, switch it off, tell me.

## Privacy: what would be sent

Today Focus sends nothing anywhere (see `PRIVACY.md`). This feature would be the first exception, so it follows strict rules:

- **Off until you turn it on and say you understand** (two separate switches: enabled, consent).
- **Your own API key**, stored on the phone. No Focus server in between.
- **Numbers only.** `DaySummary` has no text fields: tasks done and total, Shorts swiped, times the gate appeared, longest recent timer, work streak, local hour. No task names, notes, app names, PDFs or contacts.
- **Your own chat messages**, when you choose to chat.
- A **"what was sent"** screen shows the exact summary and the exact system prompt (`AiPrompt.SYSTEM`).
- One switch to turn it off, and a way to delete the chat.

The model provider you choose will see what is sent and handles it under its own terms.

## Who decides: the app, with plain rules

| Trigger | When | What the AI is asked |
|---|---|---|
| `LEFT_GATE` | you press Leave / Back out | acknowledge kindly, ask what's next or offer 5 minutes |
| `DISTRACTED` | you press "I'm distracted" | ask gently what is pulling you away |
| `REPEAT_GATE` | gate shown 3 times in 30 min | check in without judging |
| `EVENING_FULL_PLATE` | late, many tasks open | offer to shrink the list or stop for the day |
| `BIG_SHORTS_DAY` | far more Shorts than usual | be curious, not judging |
| `USER_OPENED_CHAT` | you open the chat | greet briefly, let you lead |

`AiPolicy` limits it so it cannot become a nag: off unless enabled and consented; never during quiet hours (default 22:00 to 07:00); never while a study timer runs; at most 3 unprompted messages a day; at least 45 minutes apart; the same reason waits 3 hours. When you open the chat yourself it always answers (if the feature is on).

## What it can do

Only two optional actions, each shown as a button you tap, with no parameters: `five_minute_start` (offer a 5-minute timer) and `open_timer`. Anything else the model returns is ignored (`AiReplyParser`). Replies are cleaned (control characters removed, 500 characters max).

## Safety

If a message you type sounds like you might be in danger, the app answers with a fixed, kind message in English or Arabic and does not leave it to the model (`AiSafety`). The check is a short keyword list, so it will miss things; the system prompt also tells the model what to do. If you are in danger, please talk to someone you trust or a local emergency service or helpline.

## What this branch contains so far

Phase 0 only: the rules and their tests. **No network code, no UI, not connected to the app.**

- `ai/DaySummary.kt`: the numbers the model may see
- `ai/AiPolicy.kt`: triggers and the when-may-it-speak rules
- `ai/AiPrompt.kt`: the system prompt and one instruction per trigger
- `ai/AiReply.kt`: strict parsing of the model's answer
- `ai/AiSafety.kt`: the on-phone safety check
- tests for all of the above (`app/src/test/.../ai`)

## Next, in order

1. Read and review these rules and the prompt. Try the prompt in any chat app with made-up summaries before building anything.
2. Settings: enabled, consent, API key, quiet hours; the "what was sent" screen.
3. A minimal chat screen (you open it yourself). No proactive nudges yet.
4. Wire `LEFT_GATE` and `DISTRACTED` only, with the policy above, and use it for a week.
5. Decide, with real use, whether the rest is worth it.

## Things to test before it goes near real data

Made-up conversations where the user is ashamed, angry at the app, exhausted, asking it to remove a block, joking about dying, or writing in Arabic. The answer should stay short, kind, and never agree to change a block.
