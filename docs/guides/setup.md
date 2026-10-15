# Setup guide

## 1. Grants

Open Focus. If anything is missing the home screen shows **Setup needed**. Tap it and grant, in order:

1. **Usage access**: lets Focus see which app is in front.
2. **Display over other apps**: lets it draw the gate.
3. **Accessibility service** (optional, for Shorts/Reels): find *Focus* in Accessibility settings and switch it on.
4. **Notification access** (optional): lets Focus log background music.

On Android 13+ also allow notifications.

## 2. Keep the watcher alive

Set Focus to **Unrestricted** in the phone's battery settings. Many ROMs otherwise kill the foreground service. Focus restarts it on boot and when you open the app, but a killed watcher means no gate until then.

## 3. Choose gated apps

**Setup > Gate these apps.** Every tracked app is logged; only the ones switched on show the overlay. Default: YouTube and Chrome.

## 4. YouTube picture-in-picture

**Setup > Picture-in-picture** opens the system page where you can switch YouTube PiP off, so the video cannot float over the gate.

## 5. Alerts and reminders

- Timer alerts: ring length, over-time nag interval, and whether to speak ("time's up buddy").
- Start reminders: a nudge if you have tasks but have not started, between 09:00 and 22:00.

## 6. Pick your look and language

App colour (System or custom), theme, tick shape, and language (English, Arabic, or an imported pack). See [Languages](../architecture/i18n.md).

## 7. Plan your first day

Either add subjects by hand (**+ > New card**), or paste a plan from an AI: **+ > Import / paste (.foc)**. The prompt to give the AI is in [.foc file format](../architecture/foc-files.md#asking-an-ai-to-plan-your-day).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Gate never appears | Check Usage access and Display over other apps; check the app is switched on under *Gate these apps*; set battery to Unrestricted |
| Shorts are not counted | Accessibility service is off, or the app changed its UI. Collect IDs (see [Shorts detection](../architecture/shorts-detection.md#debugging-a-broken-detector)) |
| Counts look too high | Enable root *Precise swipe counting*, or accept the scroll-based approximation |
| YouTube floats over the gate | Turn off its picture-in-picture |
| Something crashed | **Setup > Copy last crash** and attach it to an issue |
