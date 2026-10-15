# Shorts and Reels detection

Short-form feeds are detected by `FocusAccessibility` using `ShortsDetector`. The approach follows [Nudge](https://github.com/astraedus/nudge)'s in-app detector (see [third-party notices](../../THIRD_PARTY_NOTICES.md)) and was re-implemented for Focus.

## Scope

The accessibility service is limited in `accessibility_config.xml` to four packages:

| App | Package |
|---|---|
| YouTube | `com.google.android.youtube` |
| Instagram | `com.instagram.android` |
| TikTok | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill` |

It listens for window state changes, content changes and scroll events with `flagReportViewIds`. It does not see any other app. `ShortsDetector.PACKAGES` must be kept in sync with that file.

## Signals, cheapest first

`isShorts(pkg, root)` returns true as soon as one of these matches:

1. **TikTok:** being in the app counts, because the whole app is a feed.
2. **Player container ids**, looked up directly with no tree walk. These exist however the feed was opened (tab, link, DM, profile):
   - YouTube: `reel_recycler`, `reel_player_page_container`
   - Instagram: `clips_viewer_view_pager`, `clips_video_container`, `clips_media_component`
3. **Selected tab:** Instagram's `clips_tab` with `selected=true` on itself, a child or a grandchild; YouTube's bottom **Shorts** tab matched by exact label and selected on the node, a child or an ancestor. The exact-label rule means a video titled "shorts" never counts.
4. **Bounded walk** of the node tree, matching id fragments (`shorts_player`, `reel_watch_fragment`, `reel_viewer`...) for builds that rename ids slightly. The walk stops after 600 nodes.

Any exception answers "no". Apps change their UI without notice, so detection is best effort.

## Session lifecycle

- A scan runs at most every 700 ms.
- First hit starts a Shorts session and opens a `shorts` row in the usage log. Coming back within 90 s continues the same row.
- A session ends 4 s after the last hit, or when the watcher reports a different foreground app.
- While a session is active and no pass is valid, the **Shorts** overlay appears: *Just finish this one* (2 min), *Treat* (10 min) or *Use all* from the bank, and **Back out**.
- **Back out** presses Back once for YouTube/Instagram. For TikTok it exits the app by pressing Back until it is gone, then kills leftovers.

## Counting swipes

| Mode | How it counts |
|---|---|
| Default | `TYPE_VIEW_SCROLLED` events while in a session, at most one per 700 ms |
| Precise (root) | `TouchCounter` runs `getevent` through `su`, finds the multi-touch device and turns each real vertical swipe into exactly one count |

The video you land on counts as the first swipe. Without root Focus silently stays on the scroll-based count. Enable root mode under **Setup > Precise swipe counting (root)**; the idea comes from the Contre-Sozial project and the code here is new.

## Debugging a broken detector

Under **Setup**, turn on *Collect YouTube screen IDs*, open the feed, then use *Copy last captured IDs*. The copied list shows `shortsDetected=true|false` followed by the view ids on screen. Attach it to an issue, or add the new id to `PLAYER_IDS` / `FRAGMENTS`.

## Badges tied to Shorts

Shorts Diet (50 or fewer), Thumb Control (20), Zen Thumb (10), Free Shorts Day (0), Detox Week, Thumb Retirement, and the shame badges Shorts Zombie, Midnight Scroller and Shorts Marathon. See [Badges and XP](../guides/badges.md).
