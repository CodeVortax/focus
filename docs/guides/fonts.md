# Fonts

## Try a font
Settings > Appearance > **Choose a font file**, then pick a `.ttf` or `.otf`. It applies to the whole app, including notes and titles. **Reset** returns to the system font. The file is copied into the app, so you can delete the original.

## Make it stock in the source
1. Copy your font to `app/src/main/assets/fonts/default.ttf` (or `default.otf`).
2. Rebuild.

The bundled font is used until someone picks another one in Settings. Check the font's licence before shipping it in a release. Fonts under SIL OFL or Apache 2.0 are fine.

## Notes
- Variable fonts work, but only the default weight is used.
- A file Android cannot read is refused with a message.

## Example: Baloo Bhaijaan 2 (Arabic and Latin)
```sh
cp /storage/emulated/0/Download/Baloo_Bhaijaan_2/static/BalooBhaijaan2-SemiBold.ttf ~/focus/app/src/main/assets/fonts/default.ttf
cd ~/focus && git add app/src/main/assets/fonts/default.ttf && git commit -m "Bundle Baloo Bhaijaan 2 as the default font"
```
Baloo Bhaijaan 2 is under the SIL Open Font License 1.1, which allows bundling. The gate screen uses the same font.
