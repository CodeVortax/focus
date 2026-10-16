# .foc file format

`.foc` and `.fcs` are the same container. Focus never trusts the extension: the **first bytes decide what the file is**.

## Sniffing

| Header | Kind | What Focus does |
|---|---|---|
| `%PDF` | PDF | Keeps a read grant and adds it to the PDF library |
| `ftyp` at byte 4, Matroska, Ogg, FLAC, ID3, FLV, RIFF AVI/WAVE, MP3 frame | Media | Keeps a read grant and adds a **Treat** card that plays it |
| Text starting with `{` and a `"focus"` key | JSON | Applied according to the key (below) |
| Anything else | Unknown | Rejected with a message |

JSON files over 2 MB are rejected. A reply wrapped in a Markdown code fence is accepted: the fence is stripped first.

## JSON kinds

The `"focus"` key picks the kind:

| `"focus"` | Applies |
|---|---|
| `"today"` | Subjects, tasks and blocks for the day |
| `"config"` | Settings: gated apps, blocks, language |
| `"notes"` | Notes (a file with `"app": "focus-notes"` is accepted too) |
| `"lang"` | A [language pack](i18n.md#language-packs) |

### Today

```json
{
  "focus": "today",
  "replace": false,
  "subjects": [
    { "name": "Physics", "color": "#4285F4", "minutes": 90, "reward": 15,
      "tasks": ["Chapter 4 problems", "Past paper 2019"] }
  ],
  "blocks": [
    { "name": "Deep work", "from": "09:00", "to": "12:00", "lock": true,
      "strict": false, "allow": ["calculator", "pdf"] }
  ]
}
```

- A subject with the same name as an existing card gets the tasks **added** (or replacing them if `"replace": true`).
- `minutes` is the time budget, `reward` the treat minutes for finishing the subject.
- `color` is any Android colour string; unset uses the palette.
- Block times are `HH:MM`, 24 hour. `allow` takes package names or keywords that Focus resolves to installed apps.

### Config

```json
{ "focus": "config", "gated": ["youtube", "chrome"], "blocks": [], "lang": "ar" }
```

### Notes

```json
{ "focus": "notes",
  "notes": [ { "title": "Ideas", "text": "...", "labels": ["school"] },
             { "title": "To buy", "checklist": ["milk", "bread"] } ] }
```

A note with `checklist` becomes a checklist note.

## Asking an AI to plan your day

Open **Import / Export (.foc)** and press **Copy AI prompt**, give it to any AI together with your subjects, deadlines and the hours you want locked, then paste the reply under **Paste (from any AI)** and press **Import pasted**. Sharing the reply straight to Focus also works.

Focus lists what it imported ("Imported N notes", "Language added and selected: ...") or why it could not ("Could not read it: ...").

## Exporting

Under **Export**: **Today .foc**, **Config .foc**, **Notes .foc** and **Language template**.

## Source

`data/Foc.kt`: `sniffBytes`, `kindOfText`, `applyText`, `applyUri`.
