# Contributing to Focus

Thanks for helping. Focus is a small, local-only Android app, and it is easiest to keep good if changes stay small and focused.

## Ground rules

- **No network.** Do not add the `INTERNET` permission or any dependency that needs it. This is a core promise, see [PRIVACY.md](PRIVACY.md).
- **Licence.** All code is `AGPL-3.0-only`. New `.kt` / `.kts` files start with the header in [docs/LICENSE_HEADER.txt](docs/LICENSE_HEADER.txt).
- **Credit what you borrow.** If you add code or ideas from another project, keep its notice in the file and list it in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Licences must be compatible with AGPL-3.0-only.
- **Every user-facing string goes through `tr()` / `trf()`.** See [Languages](docs/architecture/i18n.md). Add the Arabic text to `data/Strings.kt` or `data/StringsExtra.kt` when you add a string.
- **Fail open.** Detection code must never crash the app. Catch, answer "no", move on.

## Getting started

```bash
git clone <your-repository-url> focus && cd focus
gradle assembleDebug
```

See [Building](docs/guides/building.md) for requirements, Termux and CI. Read [Architecture overview](docs/architecture/overview.md) first; it explains which layer owns what.

## Reporting a broken Shorts/Reels detector

YouTube, Instagram and TikTok change their screen IDs without notice. This is the most useful report you can send:

1. **Setup > Collect YouTube screen IDs** on.
2. Open the feed in the app that stopped working.
3. **Setup > Copy last captured IDs**.
4. Open an issue with the app version, Android version, the copied text, and whether `shortsDetected` was `true` or `false`.

If you can, add the new id to `PLAYER_IDS` or `FRAGMENTS` in `ShortsDetector.kt` and send a pull request.

## Bug reports

Include: Focus version (`0.16.0-beta` or newer), Android version and device, what you did, what you expected, what happened. For crashes use **Setup > Copy last crash**.

## Pull requests

- One topic per pull request.
- Build must pass `gradle assembleDebug` (CI runs it).
- Update the matching page under `docs/` when behaviour changes, and the Arabic README if a user-visible feature changes.
- Describe what changed and why.

## Adding a translation

You do not need to touch code. Follow [Languages > Translate with any AI](docs/architecture/i18n.md#translate-with-any-ai) and share the resulting `.foc` language pack.
