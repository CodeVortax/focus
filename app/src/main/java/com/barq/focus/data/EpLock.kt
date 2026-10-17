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


package com.barq.focus.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject

/**
 * Hides treat videos from every other app by renaming them (".name.mkv.focl": a leading dot and an extension nobody knows),
 * and keeps a hidden ".focus-lock.json" in the same folder that maps each hidden name back to the real one.
 * The real name is also inside the hidden name, so even if the config is lost the files can still be restored.
 */
object EpLock {
    const val SUFFIX = ".focl"
    const val CONFIG = ".focus-lock.json"

    class Child(val docId: String, val name: String, val mime: String, val size: Long, val uri: Uri)

    private fun rootDoc(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    fun list(ctx: Context, tree: Uri): List<Child> {
        val out = ArrayList<Child>()
        try {
            val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            ctx.contentResolver.query(
                kids,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE
                ), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    out.add(Child(id, c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3), DocumentsContract.buildDocumentUriUsingTree(tree, id)))
                }
            }
        } catch (_: Exception) {}
        return out
    }

    fun isMedia(c: Child) = c.mime.startsWith("video/") || c.mime.startsWith("audio/")

    private fun rename(ctx: Context, tree: Uri, doc: Uri, newName: String): Uri? =
        try {
            DocumentsContract.renameDocument(ctx.contentResolver, doc, newName)
                ?: list(ctx, tree).firstOrNull { it.name == newName }?.uri
        } catch (_: Exception) { null }

    private fun readConfig(ctx: Context, tree: Uri): JSONObject {
        val f = list(ctx, tree).firstOrNull { it.name == CONFIG } ?: return JSONObject().put("v", 1).put("files", JSONObject())
        return try {
            ctx.contentResolver.openInputStream(f.uri)?.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        } catch (_: Exception) { null } ?: JSONObject().put("v", 1).put("files", JSONObject())
    }

    private fun writeConfig(ctx: Context, tree: Uri, j: JSONObject) {
        val u = list(ctx, tree).firstOrNull { it.name == CONFIG }?.uri
            ?: try { DocumentsContract.createDocument(ctx.contentResolver, rootDoc(tree), "application/octet-stream", CONFIG) } catch (_: Exception) { null }
            ?: return
        try { ctx.contentResolver.openOutputStream(u, "wt")?.use { it.write(j.toString(2).toByteArray()) } } catch (_: Exception) {}
    }

    private fun files(cfg: JSONObject): JSONObject = cfg.optJSONObject("files") ?: JSONObject().also { cfg.put("files", it) }

    /** Renames the episodes' files so no other app sees them. Writes the config first, so a crash can always be undone. */
    fun lock(ctx: Context, tree: Uri, eps: List<Episode>): List<Episode> {
        val cfg = readConfig(ctx, tree)
        val map = files(cfg)
        eps.forEach { if (!it.locked && it.orig.isNotEmpty()) map.put("." + it.orig + SUFFIX, it.orig) }
        writeConfig(ctx, tree, cfg)
        val out = eps.map { e ->
            if (e.locked || e.orig.isEmpty()) e
            else {
                val hidden = "." + e.orig + SUFFIX
                val u = rename(ctx, tree, Uri.parse(e.uri), hidden)
                if (u != null) e.copy(uri = u.toString(), locked = true) else { map.remove(hidden); e }
            }
        }
        writeConfig(ctx, tree, cfg)
        return out
    }

    /** Gives the real names back. */
    fun unlock(ctx: Context, tree: Uri, eps: List<Episode>): List<Episode> {
        val cfg = readConfig(ctx, tree)
        val map = files(cfg)
        val out = eps.map { e ->
            if (!e.locked || e.orig.isEmpty()) e
            else {
                val u = rename(ctx, tree, Uri.parse(e.uri), e.orig)
                if (u != null) { map.remove("." + e.orig + SUFFIX); e.copy(uri = u.toString(), locked = false) } else e
            }
        }
        writeConfig(ctx, tree, cfg)
        return out
    }

    /** Developer rescue: turns every hidden file in the folder back into its real name, even for cards that are gone. */
    fun restoreFolder(ctx: Context, tree: Uri, store: Store): Int {
        val cfg = readConfig(ctx, tree)
        val map = files(cfg)
        val kids = list(ctx, tree)
        val names = kids.map { it.name }.toSet()
        val changed = HashMap<String, Uri>()
        var n = 0
        for (k in kids) {
            if (!k.name.endsWith(SUFFIX)) continue
            val orig = map.optString(k.name, "").ifEmpty { k.name.removePrefix(".").removeSuffix(SUFFIX) }
            if (orig.isEmpty() || orig in names) continue
            val u = rename(ctx, tree, k.uri, orig) ?: continue
            changed[orig] = u
            map.remove(k.name)
            n++
        }
        writeConfig(ctx, tree, cfg)
        store.cards().filter { it.folder == tree.toString() }.forEach { c ->
            store.updateCard(c.copy(eps = c.eps.map { e -> changed[e.orig]?.let { e.copy(uri = it.toString(), locked = false) } ?: e }))
        }
        return n
    }

    /** "ep2" before "ep10". */
    fun natural(a: String, b: String): Int {
        val re = Regex("\\d+|\\D+")
        val ra = re.findAll(a.lowercase()).map { it.value }.toList()
        val rb = re.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ra.size, rb.size)) {
            val x = ra[i]
            val y = rb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) x.toBigInteger().compareTo(y.toBigInteger()) else x.compareTo(y)
            if (c != 0) return c
        }
        return ra.size - rb.size
    }
}
