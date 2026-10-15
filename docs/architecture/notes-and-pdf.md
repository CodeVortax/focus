# Notes, PDF and voice

## Notes

The notes module is an independent Compose implementation that follows the *feature set* of [NotallyX](https://github.com/Crustack/NotallyX). No NotallyX source was copied (see [third-party notices](../../THIRD_PARTY_NOTICES.md)).

| Feature | Detail |
|---|---|
| Types | Text note, or checklist |
| Rich text | H1 to H3, bold, italic, underline, strikethrough, colours; stored as spans (`Rich.kt`) |
| Organisation | Colour, pin, labels, search, archive |
| Trash | Soft delete with a timestamp; *Empty trash* purges |
| Saving | Autosave about 0.7 s after the last edit |
| Backup | Export as JSON through the share sheet; import via `.foc` |
| Auto-sort | Optional (`notesAutoSort`) |

Storage is SQLite (`notes.db`, table `notes`).

### Parking a thought

Inside a subject, **Park a thought** saves an idea straight into Notes with the label *Parked* and returns you to the task. Parking five thoughts earns **Brain Dumper**.

### Voice notes

A note can hold voice recordings. `VoiceRecorder` writes AAC `.m4a` files to the app-private `voice/` folder, and each is stored on the note as a name, duration and timestamp. The microphone is used only while recording.

## PDF library and reader

| | Built-in reader | Episteme Reader |
|---|---|---|
| Engine | Android `PdfRenderer`, one page open at a time, off the main thread | The external app |
| Scroll and zoom | Continuous scroll, two-axis pinch and pan | Its own |
| Annotation | Pen, highlighter, eraser, undo, saved per PDF | Its own |
| Extras | Bookmarks, day / sepia / night themes, remembers last page | Its own |

Choose the reader under **Setup > PDF reader**. If Episteme is selected but not installed, Focus shows a message and opens the built-in reader. Episteme is not bundled; Focus hands it the file through a standard `VIEW` intent.

PDFs reach the library by opening or sharing a `.pdf` or `.foc` file or from the PDF screen. The file is not copied; Focus keeps a persisted read grant.

Reading 100 pages, or adding three PDFs, earns **Bookworm**.

## Embedded player and music

- **Treat player:** a treat card holding a video or audio file plays inside Focus with ExoPlayer, and each second of playback is paid from the [treat bank](treat-bank.md).
- **Music:** the Music screen reads your library through `MediaStore`: songs, folders, artists, playlists, favourites, queue, play next, A-B repeat, speed and pitch, sleep timer, equalizer, song info and share. Playback runs in `MusicService` as a Media3 session, so it continues in the background.
