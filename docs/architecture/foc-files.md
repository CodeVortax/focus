# .foc file format

`.foc` and `.fcs` are the same container. Focus looks at the **header**, not the extension, to decide what a file is. Implementation: `data/Foc.kt`.

## Ways in

| Entry point | How |
|---|---|
| Open a file | Android `VIEW` intent for `*.foc` and `*.fcs` (`content:` and `file:` URIs) |
| Share text | `SEND` intent with `text/plain`, so you can share an AI reply straight to Focus |
| In app | Home **+ > Import / paste (.foc)**, or Setup / Import screen |

## Detection

1. Read the first 16 bytes.
2. Binary signatures decide first:

   | Signature | Kind |
   |---|---|
   | `%PDF` | PDF: added to your library |
   | `ftyp` at offset 4, Matroska `1A 45 DF A3`, `OggS`, `fLaC`, `ID3`, `FLV`, `RIFF`+`AVI `/`WAVE`, MP3 frame sync | Media: added as a **treat card** (40 min budget) |

3. Anything else is read as UTF-8 text (max **2,000,000 bytes**), a leading ```` ```json ```` fence is stripped, and the JSON key `focus` picks the kind: `today`, `config`, `notes` or `lang`. A `{"app": "focus-notes"}` file is treated as a notes backup.
4. Otherwise: "Not a Focus file".

Media and PDF files are not copied. Focus keeps a persistable read grant on the URI.

## `today`: plan a day

```json
{
  "focus": "today", "v": 1,
  "replace": false,
  "subjects": [
    { "name": "Math", "minutes": 90, "reward": 10, "color": "#4285F4",
      "tasks": ["Solve equations 1-10", "Review mistakes"] }
  ],
  "blocks": [
    { "name": "Study", "from": "16:00", "to": "18:00",
      "lock": true, "strict": false, "allow": ["camera", "phone", "clock"] }
  ],
  "notes": [
    { "title": "Idea", "text": "...", "checklist": ["a", "b"], "labels": ["Parked"] }
  ]
}
```

| Field | Meaning |
|---|---|
| `subjects[].name` | Matched case-insensitively with an existing study card; otherwise a new card is created |
| `minutes` | Time budget (default 60) |
| `reward` | Treat minutes for the whole subject (default 10), split across tasks |
| `tasks` | Appended; duplicates (case-insensitive) are skipped |
| `replace` | `true` drops the subject's existing tasks first |
| `blocks[].from/to` | 24h `HH:mm` |
| `blocks[].allow` | Keywords or package names, see [Gate and watcher](gate-and-watcher.md#not-free-blocks) |
| `notes[].checklist` | If present the note is a checklist, otherwise a text note |

Every key except `focus` is optional.

## `config`: settings

```json
{
  "focus": "config", "v": 1,
  "settings": { "ringSec": 20, "nagMin": 5, "nagRingSec": 10, "speak": true, "notesAutoSort": true },
  "gated": ["com.google.android.youtube", "com.android.chrome"],
  "lang": "ar",
  "blocks": []
}
```

## `notes`: backup

The export from **Notes > Export backup (share)**. Each note carries type, title, body, items, colour, pinned, archived, trashed time, labels and rich-text spans. Import reports how many notes were added.

## `lang`: language pack

See [Languages and RTL](i18n.md).

## Exporting

**Import / Export** can export *today* (unfinished tasks and blocks, with `replace: true`) and *config*, saved to Downloads on Android 10+ or shared otherwise.

## Asking an AI to plan your day

Focus can generate this prompt for you (Import screen). It is:

```text
You are helping me plan my study day for the Focus app. Reply with ONLY one JSON object (no markdown, no explanation).
{"focus":"today","v":1,"replace":false,
 "subjects":[{"name":"Math","minutes":90,"reward":10,"color":"#4285F4","tasks":["Solve equations 1-10","Review mistakes"]}],
 "blocks":[{"name":"Study","from":"16:00","to":"18:00","lock":true,"strict":false,"allow":["camera","phone","clock"]}],
 "notes":[{"title":"Idea","text":"...","checklist":["a","b"],"labels":["Parked"]}]}
Rules:
- minutes = a GENEROUS time budget for the subject (I can add more time later).
- reward = treat minutes earned for finishing the whole subject; it is split across the tasks.
- blocks: lock=true means only the allowed apps work during that time. allow accepts keywords (camera, phone, clock, messages, browser, calculator, maps) or package names. strict=true removes the emergency override. Times are 24h HH:mm.
- Every key except "focus" is optional. Reuse my existing subject names when they match.
My subjects: <your current subjects>
What I need to do today: <write it here>
```

Share the AI's reply to Focus, or paste it, and the plan is applied.
