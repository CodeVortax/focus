/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Decides whether the foreground window is a short-form feed (YouTube Shorts / Instagram Reels).
 *
 * The signals follow Nudge's in-app detector, in the order it trusts them (cheapest and most
 * reliable first), and then fall back to Focus's own bounded tree walk so nothing that was
 * detected before stops being detected:
 *
 *  1. the player container ids (present whichever way the feed was opened: tab, link, DM, profile);
 *  2. the selected Shorts / Reels tab (the selection flag can sit on the tab, a child, or an ancestor);
 *  3. a bounded walk that matches id fragments, for builds that rename ids slightly.
 *
 * Best effort: apps change their UI without notice. Any failure answers "no", never an exception.
 */
object ShortsDetector {
    const val YT = "com.google.android.youtube"
    const val IG = "com.instagram.android"

    /** TikTok (global and regional builds): the whole app is a feed, so being in it counts. */
    val TIKTOK = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    /** Packages we can detect in. Keep in sync with accessibility_config.xml. */
    val PACKAGES = setOf(YT, IG) + TIKTOK

    /** Full ids of the feed player itself (looked up directly, no tree walk). */
    private val PLAYER_IDS = mapOf(
        YT to listOf(
            "$YT:id/reel_recycler",
            "$YT:id/reel_player_page_container",
        ),
        // Instagram's reel player also runs in a modal activity with no bottom nav at all
        // (a reel opened from a DM or a shared link), so tab reasoning alone can never see it.
        IG to listOf(
            "$IG:id/clips_viewer_view_pager",
            "$IG:id/clips_video_container",
            "$IG:id/clips_media_component",
        ),
    )

    /** Id fragments for the fallback walk (substring match, as before). */
    private val FRAGMENTS = mapOf(
        YT to listOf("reel_player_page_container", "reel_recycler", "shorts_player", "reel_watch_fragment"),
        IG to listOf("clips_viewer", "reel_viewer"),
    )

    /** Instagram's bottom-nav Reels tab; the active one carries selected=true on itself or a child. */
    private const val IG_REELS_TAB = "$IG:id/clips_tab"

    private const val WALK_BUDGET = 600
    private const val HARVEST_LIMIT = 800

    /** @param pkg the package of [root]; @return true when a Shorts/Reels feed is on screen. */
    fun isShorts(pkg: String, root: AccessibilityNodeInfo): Boolean = try {
        pkg in TIKTOK || directHit(pkg, root) || selectedTab(pkg, root) || walkHit(pkg, root)
    } catch (_: Exception) { false }

    private fun directHit(pkg: String, root: AccessibilityNodeInfo): Boolean {
        for (id in PLAYER_IDS[pkg].orEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val hit = nodes.isNotEmpty()
            recycle(nodes)
            if (hit) return true
        }
        return false
    }

    private fun selectedTab(pkg: String, root: AccessibilityNodeInfo): Boolean {
        if (pkg == IG) {
            val nodes = root.findAccessibilityNodeInfosByViewId(IG_REELS_TAB)
            val hit = nodes.any { tabActive(it) }
            recycle(nodes)
            return hit
        }
        // YouTube: the bottom "Shorts" tab, selected. Exact label only, so a video that merely
        // has "shorts" in its title can never count.
        val nodes = root.findAccessibilityNodeInfosByText("Shorts")
        val hit = nodes.any { n ->
            val label = (n.text ?: n.contentDescription)?.toString()
            label.equals("Shorts", ignoreCase = true) && (n.isSelected || childSelected(n) || ancestorSelected(n))
        }
        recycle(nodes)
        return hit
    }

    private fun walkHit(pkg: String, root: AccessibilityNodeInfo): Boolean {
        val frags = FRAGMENTS[pkg] ?: return false
        var budget = WALK_BUDGET
        fun walk(n: AccessibilityNodeInfo?): Boolean {
            if (n == null || budget-- <= 0) return false
            n.viewIdResourceName?.let { v ->
                val id = v.substringAfter(":id/")
                if (frags.any { id.contains(it) }) return true
            }
            if (pkg == YT && n.isSelected) {
                val t = n.text?.toString() ?: n.contentDescription?.toString()
                if (t != null && t.equals("Shorts", ignoreCase = true)) return true
            }
            for (i in 0 until n.childCount) if (walk(n.getChild(i))) return true
            return false
        }
        return walk(root)
    }

    /** selected on the node, a child, or a grandchild (Instagram flags the inner icon, not the frame). */
    private fun tabActive(n: AccessibilityNodeInfo): Boolean {
        if (n.isSelected) return true
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (c.isSelected) return true
            for (j in 0 until c.childCount) if (c.getChild(j)?.isSelected == true) return true
        }
        return false
    }

    private fun childSelected(n: AccessibilityNodeInfo): Boolean {
        for (i in 0 until n.childCount) if (n.getChild(i)?.isSelected == true) return true
        return false
    }

    private fun ancestorSelected(n: AccessibilityNodeInfo): Boolean {
        var cur = n.parent
        var depth = 0
        while (cur != null && depth < 5) {
            if (cur.isSelected) return true
            cur = cur.parent
            depth++
        }
        return false
    }

    /** Debug aid: view ids only (never text, which would be captions and messages), bounded. */
    fun harvestIds(root: AccessibilityNodeInfo): List<String> {
        val ids = LinkedHashSet<String>()
        var seen = 0
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q += root
        while (q.isNotEmpty() && seen < HARVEST_LIMIT) {
            val n = q.removeFirst()
            seen++
            n.viewIdResourceName?.let { ids += it.substringAfter(":id/") }
            for (i in 0 until n.childCount) n.getChild(i)?.let { q += it }
        }
        return ids.sorted()
    }

    private fun recycle(nodes: List<AccessibilityNodeInfo>) {
        for (n in nodes) try {
            @Suppress("DEPRECATION") n.recycle()
        } catch (_: Exception) {}
    }
}
