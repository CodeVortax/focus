# Notes, PDF and voice

## Notes

Notes live in their own SQLite database (`notes.db`, class `NotesDb`), separate from the usage log. The feature set follows [NotallyX](https://github.com/Crustack/NotallyX); the code is Focus's own (see [credits](../../THIRD_PARTY_NOTICES.md)).

| Feature | Notes |
|---|---|
| Types | Text notes and checklists |
| Rich text | H1-H3, bold, italic, underline, strikethrough, colours (`data/Rich.kt`, `ui/RichText.kt`) |
| Organise | Labels, pin, archive, trash, search |
| Backup | Export and import as JSON (`"focus": "notes"`, see [.foc file format](foc-files.md)) |
| Parked thoughts | From a subject, capture a distracting idea straight into Notes and stay on task |
| Voice notes | Record with the microphone, play back inside the note |

Opening a note from the list goes to `NoteEditorScreen`; Back returns to the list, not Home.

## Voice notes

- Recorded as AAC `.m4a` into the private `voice/` folder.
- The microphone is used **only while you hold record**.
- Voice files stay on the device.

## PDF library and reader

| | |
|---|---|
| Library | A list of PDFs you added, with title, last page and page count. Files are **not copied**: Focus keeps a persisted read grant to the file. |
| Built-in reader | Android `PdfRenderer`. Keeps Focus's look and remembers your page. |
| Episteme Reader | If installed, **Setup > PDF reader** can hand PDFs to [Episteme](https://github.com/Aryan-Raj3112/episteme) instead. It is a separate app and is not bundled. If it is not installed Focus opens the built-in reader and says so. |

Add a PDF from the library, or open one with Focus from any file manager. A `.foc` file whose header is `%PDF` is added the same way.

## Treat player and music

- **Player:** plays a treat card's video or audio, spending from the [treat bank](treat-bank.md).
- **Music:** folders, artists, playlists, favourites, queue, A-B repeat, speed and pitch, sleep timer, equalizer. Background playback runs in `MusicService`.
- Background music is logged as `music` rows when **Notification access** is on (see [Permissions](../guides/permissions.md)).

## Storage summary

| What | Where |
|---|---|
| Notes | `notes.db` |
| Voice notes | `voice/` |
| PDF entries | `Store` (preferences) |
| PDF and media files | Where you left them, by URI |
