# Focus Study Mate (separate app, optional)

**Focus Study Mate** (`com.barq.focus.ai`) is a second app that lives in this repository in the `ai/` folder. It installs next to Focus and has its own version.
The main Focus app is unchanged by it: it still has **no internet permission** and sends nothing anywhere. Only Study Mate asks for internet.

## What it does

- **Tutor chat:** asks a guiding question before giving the full answer, short and kind.
- **Explain / Quiz me / Summary / Flashcards** on text you share from any app (Share, or select text and choose "Study Mate"). You see exactly what will be sent, and the rough size, before it goes.
- **No model typing:** paste your key and Study Mate recognises the provider, asks it for its models, and picks a fast, cheap one. You can search the list and switch.
- **Survives model trouble:** if a model is "not available" or says "too many requests", Study Mate waits and retries, then tries the next best model and keeps the one that works. **Find a working model** in Settings tests the best few for you.
- **Providers:** Groq, Gemini, OpenRouter, Mistral, Cerebras, OpenAI, Anthropic, DeepSeek, or any custom address. **Connect** also tests the best few models with a tiny message and keeps one that really works for your key. The model list shows about eight recommended models, with search and "Show all".

### Free options (as of November 2026; limits change, check the provider)

| Provider | Free? | Rough limits | Note |
|---|---|---|---|
| Groq | Yes, no card | about 30 requests a minute, about 1,000 a day | not used for training |
| Gemini | Yes, no card | varies by model | Google may use free-tier data for training outside the EU, UK and EEA |
| OpenRouter | Yes, models marked `:free` | about 20 a minute, 50 a day (1,000 after a 10 dollar top-up) | study mate switches to `:free` models when you have no credit |
| Mistral | Yes, experiment tier | account-specific | Mistral may use data for training |
| Cerebras | Trial credit | finite | fast |
| OpenAI, Anthropic | No free tier | needs paid credit | |
| DeepSeek | Small trial credit | | |

Source for the numbers: [OpenRouter's comparison of free LLM APIs](https://openrouter.ai/blog/tutorials/free-llm-apis-compared/). If privacy matters for a chat, prefer a provider that says it does not train on your data.
- **Status check (can be switched off):** when a request fails for a reason that might be the provider's, Study Mate asks the public status page prismix.dev whether it has trouble. That request carries nothing about you or your chat.
- Works with any "OpenAI-compatible" provider: OpenAI, OpenRouter, Gemini, Anthropic or a custom address. You use **your own API key**, stored encrypted in the Android Keystore. No Focus server.
- Nothing is saved: the chat lives in memory and is cleared with **New** or when the app closes.

## How it keeps you safe

- Off until you add a key **and** switch on "I understand and agree".
- A worrying message ("I want to die" and similar, in English and Arabic including Egyptian) is answered on the phone with a fixed, kind message and is not sent. Later answers in that chat are told to stay gentle and point to real people. The keyword list is a safety net, not a guarantee.
- It can only talk. It cannot change Focus's limits, blocks or gate, and it cannot read Focus's data.

## Getting your notes to it

The two apps cannot read each other's data. Share text from Focus (a note's Share button) to Study Mate. A closer link can be added later if it feels clumsy.

## Build

```
gradle :ai:assembleRelease      # Study Mate
gradle :app:assembleRelease     # Focus (use this form now that there are two apps)
```

## The rules in `AiPolicy`, `AiPrompt`, `DaySummary` (phase 0)

These come from the first design, where Focus would nudge you at moments such as leaving the gate. They are not connected to anything yet and are kept, with their tests, for that later step. The original reasons are below.

## Why this exists, honestly

I'm a student too. I built Focus because I get distracted and I scroll more than I want to, and I think a calm, kind voice that notices how my day is going and helps me take one small step could actually help me, and maybe other students.

I'm not sure it will work. It might be annoying, or wrong, or not worth the privacy cost. That is why it lives on its own branch, is **off by default**, and ships only if it proves useful and safe.

## What it is not

- Not a doctor, therapist or teacher, and not a replacement for any of them.
- Not in charge. The app decides when it may speak and what it may do. The AI only puts an already-allowed moment into words.
- Not able to change your limits, blocks, passes or the gate. No setting, chat message or clever sentence can make it do that, because that code path does not exist.
- Not always right. It can say wrong or unhelpful things. Ignore it, switch it off, tell me.

## Privacy: what would be sent

Focus itself sends nothing anywhere (see `PRIVACY.md`). Study Mate is the one exception, and it is a separate app, so it follows strict rules:

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
