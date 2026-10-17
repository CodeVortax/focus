# Treat series

Tap an empty treat card and choose **A series**: pick the folder, then the episodes.

- **One token = one episode.** Starting an episode spends its length (rounded up to whole minutes) from the treat bank. You get that episode only, plus **5 free minutes** to seek back. Quit halfway and the rest of the token is kept.
- An episode counts as watched when it ends or you stop in its last minute. The next one needs another token.

## Lock the files
Switch **Lock these files** on and Focus renames each file to `.<name>.focl`: hidden, and with an extension no app knows. A hidden `.focus-lock.json` in the same folder maps each hidden name to the real one. Use the card's menu to lock or unlock later.

Android does not let apps use the root of the storage or the Download folder as a picked folder; use a subfolder.

## If something goes wrong
Developer options (tap the version in Settings 7 times) > **Restore locked episode files**: pick the folder and every hidden file gets its name back, using the config or the name hidden inside the file name.
