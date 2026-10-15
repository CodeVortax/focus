# Credits and third-party notices

Focus is licensed under the **GNU Affero General Public License v3.0 only** (`AGPL-3.0-only`, see `LICENSE`).

## Projects that inspired Focus

| Project | Authors | License | Link |
|---|---|---|---|
| **NotallyX** (notes app) | Crustack and contributors | GPL-3.0 | https://github.com/Crustack/NotallyX |
| **Notally** (the project NotallyX is forked from) | OmGodse | GPL-3.0 | https://github.com/OmGodse/Notally |
| **Episteme Reader** (document reader) | Aryan-Raj3112 and contributors | AGPL-3.0-only | https://github.com/Aryan-Raj3112/episteme |
| **Contre-Sozial** (touch-event scroll detection idea) | its authors | see its repository | referenced in the project notes |

How they relate to this code, honestly:

* The **notes module** (checklists, labels, colours, pin/archive/trash, search, backup) is an independent
  implementation in Jetpack Compose that follows the *feature set* of NotallyX. No NotallyX source file was copied.
* **Episteme Reader** is used as an external app: Focus can hand a PDF to it through a standard `VIEW` intent.
  Episteme is not bundled and none of its source is included. Focus also has a small built-in PDF reader.
* The optional **precise swipe counter** reads touchscreen events with `getevent`, an approach shown by Contre-Sozial.
  The code here is new.

If code from any of these projects is added later, keep its copyright notice in the file it lands in and list it here.
Those licenses (GPL-3.0 / AGPL-3.0) are compatible with distributing Focus under AGPL-3.0-only.

## Libraries used

| Library | License |
|---|---|
| AndroidX / Jetpack Compose / Material 3 | Apache-2.0 |
| AndroidX Media3 (ExoPlayer) | Apache-2.0 |
| Baloo 2 font (Ek Type) | SIL Open Font License 1.1 |
| Pdfium via `io.legere:pdfiumandroid` (PDFium build only) | Apache-2.0 / BSD-3-Clause |

## formulas-games (Apache License 2.0)

Source: https://github.com/GuillaumeFalourd/formulas-games

The game ideas and rules of Snake, Brick Breaker, Flappy Bird, Dino Run, Yahtzee and Space Invasion in the
Games section were taken from this project (Apache-2.0, Copyright its authors). The games were rewritten from
scratch in Kotlin / Jetpack Compose for touch screens and everything is drawn in code: no images, sounds or
other assets from the original were copied.
