# Look & theme

Home menu → **Look & theme**. Pick a preset, try it, apply it. Or export the theme as JSON, change it anywhere (a text editor, an AI) and import it back.

A theme is **design values only**. It can never change what the app does: the gate, the treat bank, blocks and the Shorts counter ignore it.

## What a theme controls

| Field | Meaning |
|---|---|
| `themeName` | 1 to 40 letters, digits or simple punctuation |
| `appearance` | `system`, `light` or `dark`. Decides when `darkColors` is used |
| `colors` | `primary`, `secondary`, `accent`, `background`, `surface`, `card`, `textPrimary`, `textSecondary`, `success`, `warning`, `error`, each `#RRGGBB` |
| `darkColors` | Optional. The same eleven colours for the dark look. Without it, `colors` is always used |
| `shapes` | `cardRadius`, `buttonRadius`: whole numbers 0 to 40 |
| `spacing` | `compact`, `normal`, `comfortable` (space between blocks on the home screen) |
| `typography.scale` | 0.85 to 1.3, on top of the phone's own text size |
| `components.home` | `showGreeting`, `showProgress` (true/false); `primaryAction`: `subtle`, `normal`, `high` |
| `components.gate` | `supportiveMessage` (true/false); `visualStyle`: `calm` (breathing orb) or `plain` (still) |

Keys that start with `_` (`_about`, `_fields`) are notes for humans and AIs; they must be plain text and are ignored.

## What gets rejected (nothing is applied)

- Not JSON, longer than 16,000 characters, or an unknown `schemaVersion` (the only one is `1`).
- Any field that is not in the table: unknown keys at any level, so a theme can't carry anything that looks like an instruction.
- A colour that is not `#RRGGBB`, a number out of range, a value that is not one of the listed words.
- **Text that is too faint.** `textPrimary` must reach contrast 4.5 on `background`, `surface` and `card`; `textSecondary` 4.5 on `background` and `card`; `primary` 3.0 on `background` (WCAG relative luminance). Checked for `darkColors` too.

The error card lists the first eight problems in plain words. The saved theme stays as it was.

## Where it applies

- All Compose screens: colours, card radius (through the Material shape scale, so dialogs and chips follow), text scale.
- The gate, the not-free screen and the Shorts gate (they are drawn with plain views, so they read the same JSON): colours, the supportive line on/off, still or breathing orb.
- Home: greeting, progress card, spacing, the Start button's weight and radius.
- Material buttons elsewhere keep their pill shape; only the home Start button uses `buttonRadius`.
- `success` and `warning` are stored and exported for screens that want them; no existing screen uses them yet.
- While a theme is applied it replaces the accent and light/dark choices from Setup. Choose **Focus default** to go back to those.

## Using an AI

1. **Copy theme**, then **Copy AI prompt**. Paste both into the AI.
2. Ask for the look you want ("calmer, warmer, less contrast between cards").
3. Paste the answer into **Paste a theme here** and press **Preview**. Apply it or cancel.

If the AI breaks a rule (a faint colour, an extra field), Focus tells you which one; paste that message back to the AI.

## For developers

`ui/theme/ThemeConfig.kt` is plain Kotlin (model, `ThemeJson.parse`/`toJson`, presets); `ThemeJsonTest` covers it on the JVM. `AppTheme.saved` and `AppTheme.preview` are Compose state read by `FocusTheme`; the overlay parses `Store.themeJson` itself because it has no Compose host. To add a field: add it to `ThemeConfig`, `parse`, `toJson`, the presets, and a test.
