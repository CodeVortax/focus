# First-run guide

On a fresh install Focus opens a short guide:

1. **Welcome**: pick the language (English, Arabic, Russian, German, French; the phone's language is preselected when supported).
2. **How it works**: plan, earn, spend, stay in control, keep notes.
3. **Permissions**: usage access, display over other apps, accessibility, notifications, with live status.
4. **Your subjects**: remove the sample cards and add your own.
5. **Music and notifications**: steps for your phone maker's capsule and battery settings.
6. **Done.**

Skip leaves at any step. Existing installs that update do not see it; **Settings > About > Replay the first-run guide** opens it again. Implemented in `ui/Onboarding.kt`; the flag is `Store.onboarded` and a fresh install is detected by `Store.isFresh()` before the first cards are written.
