# Shorts and Reels detection

`FocusAccessibility` is an accessibility service limited to **YouTube**, **Instagram** and **TikTok**. It decides whether a short-form feed is on screen, counts swipes, and offers the gate.

```
accessibility event (scroll, content change)
  -> package in ShortsDetector.PACKAGES ?
  -> at most one scan per 700 ms
  -> ShortsDetector.isShorts(pkg, root)
  -> found: start (or continue) a "shorts" row, show the gate after the grace period
  -> not seen for 4 s, or another app in front: close the row
```

## Packages

| App | Package |
|---|---|
| YouTube | `com.google.android.youtube` |
| Instagram | `com.instagram.android` |
| TikTok | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill` |

The list lives in two places that must match: `ShortsDetector.PACKAGES` and `res/xml/accessibility_config.xml`.

## The detector (`ShortsDetector.kt`)

Signals are tried cheapest and most reliable first. The first three are Nudge's in-app detector signals; the fourth is Focus's original walk, kept so nothing that used to be detected stops working.

| # | Signal | Apps |
|---|---|---|
| 0 | The whole app is a feed | TikTok |
| 1 | A **player container id** exists: `reel_recycler`, `reel_player_page_container` (YouTube); `clips_viewer_view_pager`, `clips_video_container`, `clips_media_component` (Instagram) | YouTube, Instagram |
| 2 | The **Shorts / Reels tab is selected** (the flag may sit on the tab, a child, a grandchild or an ancestor) | both |
| 3 | A bounded **tree walk** (600 nodes) matches an id fragment such as `shorts_player`, `reel_watch_fragment`, `clips_viewer`, `reel_viewer` | both |

Notes:

- The Instagram player runs in its own modal screen with **no bottom bar** when a reel is opened from a DM or a link. Tab reasoning cannot see it; the container ids can.
- The YouTube tab check wants the label to be **exactly** "Shorts", so a video that merely has "shorts" in its title never counts.
- Any exception means "no". Detection is best effort and must never crash.
- TikTok counts the whole app, so scrolling its inbox or a profile also adds swipes.

## Counting swipes

| Mode | How |
|---|---|
| Default | Each `TYPE_VIEW_SCROLLED` event while in a feed counts once, with a 700 ms debounce. The video you land on counts as the first. |
| Root (optional) | `TouchCounter` runs `su -c getevent` and counts real swipes. Turn it on under **Setup > Precise swipe counting (root)**. Needs root; without it Focus keeps the scroll count. |

Counts are stored per day in `Store` and per visit in the `usage` row (`n`).

## Visits and rows

A visit is one `usage` row of kind `shorts`. Returning to the same app within 90 seconds reopens the same row, so a few quick exits are one visit. A visit ends when no feed has been seen for 4 seconds or when the foreground app changes.

## The Shorts gate

After the grace period the **Shorts** gate (**TikTok** for TikTok) appears:

| Choice | Result |
|---|---|
| **Just finish this one** | 2 minutes |
| **Treat** | Spend 10 minutes from the bank |
| **Use all** | Spend the whole bank |
| **Back out** | Press Back once; the cooldown stops it re-detecting during the transition. TikTok uses the full leave sequence instead. |

It shows today's swipes and minutes. Same look as the [app gate](gate-and-watcher.md#the-gate-screen).

## Debugging a broken detector

Apps rename view ids without notice. To capture them:

1. **Setup > Collect YouTube screen IDs** on.
2. Open the feed that stopped being detected.
3. **Setup > Copy last captured IDs**.

The capture holds `shortsDetected=true/false` and the list of view ids (ids only, never text). Attach it to an issue, or add the new id to `PLAYER_IDS` or `FRAGMENTS` in `ShortsDetector.kt`. See [Contributing](../../CONTRIBUTING.md).

## Privacy

The service reads view ids and the selected flag, nothing else. It never reads message text, captions or anything you type, and stores only counts and times. See [Permissions](../guides/permissions.md).
