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

package com.barq.focus.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.barq.focus.data.Song
import com.barq.focus.data.MusicLibrary
import com.barq.focus.data.Store
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.service.Music
import com.barq.focus.service.Eq
import com.barq.focus.ui.theme.FocusBrandFont
import com.barq.focus.ui.theme.FocusNoteFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class MTab { QUEUE, NOW, FOLDERS, ALBUMS, ARTISTS, PLAYLISTS, SEARCH }

private class Album(val id: Long, val name: String, val artist: String, val songs: List<Song>)

/** Drops the cover-art bitmaps (called when the app leaves the screen, so a background player stays small). */
fun trimMusicArt() = artCache.evictAll()

private val artCache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 32).toInt()) {
    override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
}

private fun artKey(s: Song) = if (s.albumId > 0) s.albumId else -s.id

private suspend fun loadArt(ctx: Context, s: Song, px: Int): Bitmap? = withContext(Dispatchers.IO) {
    artCache.get(artKey(s)) ?: run {
        var b: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29) try { b = ctx.contentResolver.loadThumbnail(s.uri, Size(px, px), null) } catch (_: Exception) {}
        if (b == null) try {
            val r = MediaMetadataRetriever()
            r.setDataSource(ctx, s.uri)
            r.embeddedPicture?.let { b = BitmapFactory.decodeByteArray(it, 0, it.size) }
            r.release()
        } catch (_: Exception) {}
        b?.also { artCache.put(artKey(s), it) }
    }
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Art(song: Song?, modifier: Modifier, px: Int = 300, corner: Dp = 12.dp) {
    val ctx = LocalContext.current
    var bmp by remember(song?.id) { mutableStateOf(song?.let { artCache.get(artKey(it)) }) }
    LaunchedEffect(song?.id) { if (song != null && bmp == null) bmp = loadArt(ctx, song, px) }
    Box(modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

private fun openEqualizer(ctx: Context) {
    val i = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, Music.player?.audioSessionId ?: 0)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    try { ctx.startActivity(i) } catch (_: Exception) { Toast.makeText(ctx, tr("No equalizer found on this phone"), Toast.LENGTH_SHORT).show() }
}

private fun shareSong(ctx: Context, s: Song) {
    try {
        ctx.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(s.mime).putExtra(Intent.EXTRA_STREAM, s.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), tr("Share song")
            )
        )
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, reload) {
        if (granted) { loading = true; songs = withContext(Dispatchers.IO) { MusicLibrary.load(ctx) }; loading = false }
    }
    val byId = remember(songs) { songs.associateBy { it.id } }
    LaunchedEffect(songs) { if (songs.isNotEmpty()) Music.restore(ctx, byId) }     // the last song is ready and paused, right where you left it
    val current = byId[Music.currentId]

    var tab by remember { mutableStateOf(MTab.NOW) }
    var sub by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var favs by remember { mutableStateOf(store.favorites()) }
    var playlists by remember { mutableStateOf(store.playlists()) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var addTo by remember { mutableStateOf<Song?>(null) }
    var infoOf by remember { mutableStateOf<Song?>(null) }
    var showRepeat by remember { mutableStateOf(false) }
    var showAB by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    BackHandler(enabled = sub != null) { sub = null }
    // any page goes back to the main player first; one more back leaves the music screen
    BackHandler(enabled = sub == null && tab != MTab.NOW) { tab = MTab.NOW }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun play(list: List<Song>, i: Int) { Music.playSongs(ctx, list, i); tab = MTab.NOW; sub = null }
    fun withPlaylist(name: String, ids: (List<Long>) -> List<Long>) {
        val m = LinkedHashMap(store.playlists())
        m[name] = ids(m[name] ?: emptyList())
        store.savePlaylists(m); playlists = m
    }

    Scaffold(
        bottomBar = {
            Column {
                if (current != null && (tab != MTab.NOW || sub != null)) MiniNow(
                    current, favs.contains(current.id),
                    onOpen = { tab = MTab.NOW; sub = null },
                    onFav = { favs = store.toggleFavorite(current.id); com.barq.focus.service.MusicService.refresh() }
                )
                MusicBar(
                    tab, { tab = it; sub = null },
                    { reload++ }, { showSleep = true }, { showEq = true },
                    { Music.stopAll(ctx); toast(tr("Player closed")) }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !granted -> Column(
                    Modifier.align(Alignment.Center).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(tr("Music"), style = MaterialTheme.typography.headlineSmall, fontFamily = FocusBrandFont, fontWeight = FontWeight.Bold)
                    Text(tr("Focus needs permission to read the music on your phone."), textAlign = TextAlign.Center)
                    Button(onClick = { launcher.launch(perm) }) { Text(tr("Allow")) }
                    TextButton(onClick = onBack) { Text(tr("Back")) }
                }
                loading && songs.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                sub != null -> {
                    val (title, list) = sub!!
                    SongList(
                        list, favs, { play(list, it) }, { menuSong = it },
                        header = {
                            SubHeader(title, list.size, { sub = null }, { play(list, 0) }, {
                                Music.get(ctx).shuffleModeEnabled = true; play(list.shuffled(), 0)
                            })
                        }
                    )
                }
                else -> when (tab) {
                    MTab.NOW -> NowPage(
                        current, false,
                        favs.contains(current?.id), 
                        { current?.let { favs = store.toggleFavorite(it.id); com.barq.focus.service.MusicService.refresh() } },
                        { infoOf = current }, { addTo = current }, { showMore = true },
                        { showRepeat = true },
                        {
                            val p = Music.get(ctx)
                            p.shuffleModeEnabled = !p.shuffleModeEnabled
                            toast(tr(if (p.shuffleModeEnabled) "Shuffle mode is on" else "Shuffle mode is off"))
                        },
                        { showVolume = true }, { showEq = true }, onBack, { tab = MTab.FOLDERS }
                    )
                    MTab.QUEUE -> QueuePage(byId)
                    MTab.FOLDERS -> {
                        val groups = remember(songs) { songs.groupBy { it.folder }.entries.sortedBy { it.key.lowercase() } }
                        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                            item { PageTitle(tr("Folders")) }
                            items(groups, key = { it.key }) { g ->
                                ListRow(Icons.Default.Folder, g.key.substringAfterLast('/').ifBlank { "/" }, g.key + "  ·  " + trf("%d songs", g.value.size)) {
                                    sub = g.key.substringAfterLast('/') to g.value
                                }
                            }
                        }
                    }
                    MTab.ALBUMS -> {
                        val albums = remember(songs) {
                            songs.groupBy { it.albumId }.map { (id, l) -> Album(id, l.first().album, l.first().artist, l.sortedBy { it.track }) }
                                .sortedBy { it.name.lowercase() }
                        }
                        LazyVerticalGrid(
                            GridCells.Fixed(2), contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(albums, key = { it.id }) { a ->
                                Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { sub = a.name to a.songs }) {
                                    Art(a.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), 400, 16.dp)
                                    Text(a.name, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    MTab.ARTISTS -> {
                        val artists = remember(songs) { songs.groupBy { it.artist }.entries.sortedBy { it.key.lowercase() } }
                        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                            item { PageTitle(tr("Artists")) }
                            items(artists, key = { it.key }) { a ->
                                ListRow(Icons.Default.Person, a.key, trf("%d songs", a.value.size)) { sub = a.key to a.value }
                            }
                        }
                    }
                    MTab.PLAYLISTS -> LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                        item { PageTitle(tr("Playlists")) }
                        item {
                            ListRow(Icons.Default.Favorite, tr("Favorites"), trf("%d songs", favs.size)) {
                                sub = tr("Favorites") to songs.filter { it.id in favs }
                            }
                        }
                        items(playlists.entries.toList(), key = { it.key }) { e ->
                            ListRow(
                                Icons.Default.LibraryMusic, e.key, trf("%d songs", e.value.count { it in byId }),
                                trailing = {
                                    IconButton(onClick = {
                                        val m = LinkedHashMap(playlists); m.remove(e.key); store.savePlaylists(m); playlists = m
                                    }) { Icon(Icons.Default.Delete, "Delete") }
                                }
                            ) { sub = e.key to e.value.mapNotNull { byId[it] } }
                        }
                        item {
                            OutlinedButton(onClick = { newPlaylist = true }, Modifier.padding(16.dp)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("New playlist"))
                            }
                        }
                    }
                    MTab.SEARCH -> {
                        var q by remember { mutableStateOf("") }
                        val hits = remember(q, songs) {
                            val t = q.trim().lowercase()
                            if (t.isEmpty()) emptyList() else songs.filter {
                                it.title.lowercase().contains(t) || it.artist.lowercase().contains(t) || it.album.lowercase().contains(t)
                            }
                        }
                        SongList(
                            hits, favs, { play(hits, it) }, { menuSong = it },
                            header = {
                                OutlinedTextField(
                                    q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text(tr("Search songs")) },
                                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ dialogs
    menuSong?.let { s ->
        AlertDialog(
            onDismissRequest = { menuSong = null }, confirmButton = {},
            title = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ActionRow(Icons.Default.PlayArrow, tr("Play now")) { menuSong = null; play(listOf(s), 0) }
                    ActionRow(Icons.Default.QueueMusic, tr("Play next")) {
                        menuSong = null
                        val p = Music.get(ctx)
                        if (p.mediaItemCount == 0) play(listOf(s), 0) else p.addMediaItem(p.currentMediaItemIndex + 1, Music.item(s))
                        toast(tr("Added to the queue"))
                    }
                    ActionRow(Icons.Default.Add, tr("Add to the queue")) {
                        menuSong = null
                        val p = Music.get(ctx)
                        if (p.mediaItemCount == 0) play(listOf(s), 0) else p.addMediaItem(Music.item(s))
                        toast(tr("Added to the queue"))
                    }
                    ActionRow(Icons.Default.PlaylistAdd, tr("Add to playlists")) { menuSong = null; addTo = s }
                    ActionRow(Icons.Default.Share, tr("Share song")) { menuSong = null; shareSong(ctx, s) }
                    ActionRow(Icons.Default.Info, tr("Song info")) { menuSong = null; infoOf = s }
                }
            }
        )
    }

    if (showMore && current != null) AlertDialog(
        onDismissRequest = { showMore = false }, confirmButton = {},
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ActionRow(Icons.Default.Repeat, tr("A-B repeat")) { showMore = false; showAB = true }
                ActionRow(Icons.Default.FastForward, tr("Play speed and pitch")) { showMore = false; showSpeed = true }
                ActionRow(Icons.Default.Pause, tr("Sleep timer")) { showMore = false; showSleep = true }
                HorizontalDivider()
                ActionRow(Icons.Default.QueueMusic, tr("Add to the queue")) {
                    showMore = false
                    Music.get(ctx).addMediaItem(Music.item(current)); toast(tr("Added to the queue"))
                }
                ActionRow(Icons.Default.PlaylistAdd, tr("Add to playlists")) { showMore = false; addTo = current }
                HorizontalDivider()
                ActionRow(Icons.Default.Share, tr("Share song")) { showMore = false; shareSong(ctx, current) }
                ActionRow(Icons.Default.Info, tr("Song info")) { showMore = false; infoOf = current }
                ActionRow(Icons.Default.Equalizer, tr("Equalizer")) { showMore = false; showEq = true }
            }
        }
    )

    addTo?.let { s ->
        var sel by remember(s) { mutableStateOf(setOf<String>()) }
        var fresh by remember(s) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addTo = null },
            title = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(fresh, { fresh = it }, singleLine = true, label = { Text(tr("New playlist")) })
                    Row(Modifier.fillMaxWidth().clickable { sel = if ("★" in sel) sel - "★" else sel + "★" }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox("★" in sel, { sel = if (it) sel + "★" else sel - "★" }); Text(tr("Favorites"))
                    }
                    playlists.keys.forEach { n ->
                        Row(Modifier.fillMaxWidth().clickable { sel = if (n in sel) sel - n else sel + n }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(n in sel, { sel = if (it) sel + n else sel - n }); Text(n)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    sel.forEach { n ->
                        if (n == "★") { if (s.id !in favs) favs = store.toggleFavorite(s.id) }
                        else withPlaylist(n) { (it + s.id).distinct() }
                    }
                    if (fresh.isNotBlank()) withPlaylist(fresh.trim()) { (it + s.id).distinct() }
                    addTo = null
                    toast(tr("Added"))
                }) { Text(tr("Add")) }
            },
            dismissButton = { TextButton(onClick = { addTo = null }) { Text(tr("Cancel")) } }
        )
    }

    if (newPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newPlaylist = false },
            title = { Text(tr("New playlist")) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Name")) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { withPlaylist(name.trim()) { it }; newPlaylist = false }) { Text(tr("Add")) }
            },
            dismissButton = { TextButton(onClick = { newPlaylist = false }) { Text(tr("Cancel")) } }
        )
    }

    infoOf?.let { s ->
        AlertDialog(
            onDismissRequest = { infoOf = null },
            title = { Text(tr("Song info")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow(tr("File name"), s.fileName)
                    InfoRow(tr("Location"), s.folder)
                    InfoRow(tr("Title"), s.title)
                    InfoRow(tr("Album"), s.album)
                    InfoRow(tr("Artist"), s.artist)
                    InfoRow(tr("Duration"), mmss(s.durationMs))
                    if (s.track > 0) InfoRow(tr("Track number"), s.track.toString())
                    if (s.year > 0) InfoRow(tr("Year"), s.year.toString())
                    InfoRow(tr("Size"), "%.1f MB".format(s.size / 1048576.0))
                    InfoRow(tr("Format"), s.mime)
                }
            },
            confirmButton = { TextButton(onClick = { infoOf = null }) { Text(tr("Done")) } }
        )
    }

    if (showRepeat) AlertDialog(
        onDismissRequest = { showRepeat = false },
        title = { Text(tr("Repeat")) },
        text = {
            Column {
                listOf(
                    Triple(Player.REPEAT_MODE_OFF, "Play next song", Icons.Default.PlayArrow),
                    Triple(Player.REPEAT_MODE_ALL, "Repeat the same queue", Icons.Default.Repeat),
                    Triple(Player.REPEAT_MODE_ONE, "Repeat the same song", Icons.Default.RepeatOne),
                ).forEach { (mode, label, icon) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Music.get(ctx).repeatMode = mode; showRepeat = false }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(Music.repeatMode == mode, { Music.get(ctx).repeatMode = mode; showRepeat = false })
                        Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(tr(label))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showRepeat = false }) { Text(tr("Done")) } }
    )

    if (showAB) {
        val p = Music.get(ctx)
        AlertDialog(
            onDismissRequest = { showAB = false },
            title = { Text(tr("A-B repeat")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A: " + (if (Music.abA >= 0) mmss(Music.abA) else "—") + "      B: " + (if (Music.abB >= 0) mmss(Music.abB) else "—"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Music.abA = p.currentPosition; Music.kick() }) { Text(tr("Set A here")) }
                        Button(onClick = { if (p.currentPosition > Music.abA) { Music.abB = p.currentPosition; Music.kick() } }) { Text(tr("Set B here")) }
                    }
                    Text(tr("The part between A and B repeats until you clear it."), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showAB = false }) { Text(tr("Done")) } },
            dismissButton = { TextButton(onClick = { Music.abA = -1L; Music.abB = -1L }) { Text(tr("Clear")) } }
        )
    }

    if (showSpeed) {
        val p = Music.get(ctx)
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text(tr("Play speed and pitch")) },
            text = {
                Column {
                    Text(trf("Speed  %s×", "%.2f".format(Music.speed)))
                    Slider(Music.speed, { p.playbackParameters = PlaybackParameters(it, Music.pitch) }, valueRange = 0.5f..2f)
                    Text(trf("Pitch  %s×", "%.2f".format(Music.pitch)))
                    Slider(Music.pitch, { p.playbackParameters = PlaybackParameters(Music.speed, it) }, valueRange = 0.5f..2f)
                }
            },
            confirmButton = { TextButton(onClick = { showSpeed = false }) { Text(tr("Done")) } },
            dismissButton = { TextButton(onClick = { p.playbackParameters = PlaybackParameters(1f, 1f) }) { Text(tr("Reset")) } }
        )
    }

    if (showSleep) AlertDialog(
        onDismissRequest = { showSleep = false },
        title = { Text(tr("Sleep timer")) },
        text = {
            Column {
                if (Music.sleepAt > 0) Text(trf("Pauses in %d min", ((Music.sleepAt - System.currentTimeMillis()) / 60_000L + 1).toInt()), color = MaterialTheme.colorScheme.primary)
                listOf(10, 20, 30, 45, 60, 90).forEach { m ->
                    TextButton(onClick = { Music.setSleep(m); showSleep = false }) { Text(trf("%d minutes", m)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSleep = false }) { Text(tr("Done")) } },
        dismissButton = { TextButton(onClick = { Music.setSleep(0); showSleep = false }) { Text(tr("Turn off")) } }
    )

    if (showEq) EqualizerDialog(onClose = { showEq = false }, onSystem = { openEqualizer(ctx) })

    if (showVolume) {
        val am = remember { ctx.getSystemService(AudioManager::class.java) }
        val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
        var v by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
        AlertDialog(
            onDismissRequest = { showVolume = false },
            title = { Text(tr("Volume")) },
            text = {
                Slider(v, { v = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0) }, valueRange = 0f..max.toFloat(), steps = (max - 1).coerceAtLeast(0))
            },
            confirmButton = { TextButton(onClick = { showVolume = false }) { Text(tr("Done")) } }
        )
    }
}

// ------------------------------------------------------------------------------------------ pieces

@Composable
private fun MusicBar(tab: MTab, onTab: (MTab) -> Unit, onRefresh: () -> Unit, onSleep: () -> Unit, onEq: () -> Unit, onClose: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val items = listOf(
        MTab.QUEUE to Icons.Default.QueueMusic, MTab.NOW to Icons.Default.PlayCircleFilled, MTab.FOLDERS to Icons.Default.Folder,
        MTab.ALBUMS to Icons.Default.Album, MTab.ARTISTS to Icons.Default.Person, MTab.PLAYLISTS to Icons.Default.LibraryMusic,
        MTab.SEARCH to Icons.Default.Search,
    )
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (t, icon) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { onTab(t) }) {
                        Icon(icon, null, tint = if (tab == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(width = 18.dp, height = 3.dp).clip(CircleShape).background(if (tab == t) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
            Box(Modifier.weight(1f)) {
                IconButton(onClick = { menu = true }, Modifier.align(Alignment.Center)) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Refresh library")) }, onClick = { menu = false; onRefresh() })
                    DropdownMenuItem(text = { Text(tr("Sleep timer")) }, onClick = { menu = false; onSleep() })
                    DropdownMenuItem(text = { Text(tr("Equalizer")) }, onClick = { menu = false; onEq() })
                    DropdownMenuItem(text = { Text(tr("Close player")) }, onClick = { menu = false; onClose() })
                }
            }
        }
    }
}

@Composable
private fun PageTitle(t: String) {
    Text(t, Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall, fontFamily = FocusBrandFont, fontWeight = FontWeight.Bold)
}

@Composable
private fun SubHeader(title: String, count: Int, onBack: () -> Unit, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FocusBrandFont, fontWeight = FontWeight.Bold)
                Text(trf("%d songs", count), style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlay, enabled = count > 0) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(tr("Play")) }
            OutlinedButton(onClick = onShuffle, enabled = count > 0) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(6.dp)); Text(tr("Shuffle")) }
        }
    }
}

@Composable
private fun ListRow(icon: ImageVector, title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(text)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SongList(list: List<Song>, favs: Set<Long>, onPlay: (Int) -> Unit, onMenu: (Song) -> Unit, header: @Composable () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
        item { header() }
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            val playing = Music.currentId == s.id
            Row(Modifier.fillMaxWidth().clickable { onPlay(i) }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(s, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                    )
                    Text(
                        s.artist + "  ·  " + mmss(s.durationMs), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (s.id in favs) Icon(Icons.Default.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { onMenu(s) }) { Icon(Icons.Default.MoreVert, "More") }
            }
        }
        if (list.isEmpty()) item { Text(tr("Nothing here yet."), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun QueuePage(byId: Map<Long, Song>) {
    val ctx = LocalContext.current
    val p = Music.player
    val items = remember(Music.version) {
        if (p == null) emptyList() else (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PageTitle(trf("Queue  (%d)", items.size))
                Spacer(Modifier.weight(1f))
                if (items.isNotEmpty()) TextButton(onClick = { p?.clearMediaItems() }, Modifier.padding(end = 8.dp)) { Text(tr("Clear")) }
            }
        }
        itemsIndexed(items) { i, it ->
            val s = byId[it.mediaId.toLongOrNull()]
            val playing = p != null && p.currentMediaItemIndex == i
            Row(Modifier.fillMaxWidth().clickable { p?.seekToDefaultPosition(i); p?.play() }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(s, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (s?.title ?: it.mediaMetadata.title?.toString() ?: "?"), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text((s?.artist ?: it.mediaMetadata.artist?.toString() ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = { p?.removeMediaItem(i) }) { Icon(Icons.Default.Close, "Remove") }
            }
        }
        if (items.isEmpty()) item { Text(tr("The queue is empty."), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun NowPage(
    current: Song?, @Suppress("UNUSED_PARAMETER") unused: Boolean, fav: Boolean,
    onFav: () -> Unit, onInfo: () -> Unit, onAddPlaylist: () -> Unit, onMore: () -> Unit,
    onRepeat: () -> Unit, onShuffle: () -> Unit, onVolume: () -> Unit, onEq: () -> Unit, onBack: () -> Unit, onLibrary: () -> Unit,
) {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val p = Music.player
            if (p != null && drag == null) { pos = p.currentPosition; dur = p.duration.coerceAtLeast(0L) }
            delay(350)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Art(current, Modifier.fillMaxHeight().aspectRatio(1f), 700, 28.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            current?.title ?: tr("Nothing playing"), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FocusNoteFont, fontWeight = FontWeight.Bold, textDirection = TextDirection.Content)
        )
        Text(current?.artist ?: "", color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(
            current?.let { it.album + if (it.track > 0) "  -  " + it.track else "" } ?: "",
            color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium
        )
        if (current == null) TextButton(onClick = onLibrary) { Text(tr("Pick something to play")) }

        Spacer(Modifier.height(10.dp))
        LtrRow(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFav, enabled = current != null) { Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", Modifier.size(30.dp), tint = if (fav) primary else dim) }
            IconButton(onClick = onInfo, enabled = current != null) { Icon(Icons.Default.Info, "Info", tint = dim) }
            IconButton(onClick = onAddPlaylist, enabled = current != null) { Icon(Icons.Default.PlaylistAdd, "Playlist", tint = dim) }
            IconButton(onClick = onMore, enabled = current != null) { Icon(Icons.Default.MoreHoriz, "More", tint = dim) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRepeat) {
                Icon(
                    if (Music.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat",
                    tint = if (Music.repeatMode == Player.REPEAT_MODE_OFF) dim.copy(alpha = 0.6f) else primary
                )
            }
            IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (Music.shuffle) primary else dim.copy(alpha = 0.6f)) }
        }

        LtrRow(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(mmss(drag?.toLong() ?: pos), style = MaterialTheme.typography.labelLarge, color = dim)
            Slider(
                drag ?: pos.toFloat().coerceAtMost(dur.coerceAtLeast(1L).toFloat()),
                { drag = it },
                Modifier.weight(1f).padding(horizontal = 6.dp),
                valueRange = 0f..dur.coerceAtLeast(1L).toFloat(),
                onValueChangeFinished = { drag?.let { Music.player?.seekTo(it.toLong()) }; drag = null }
            )
            Text(mmss(dur), style = MaterialTheme.typography.labelLarge, color = dim)
        }

        LtrRow(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Music.player?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(32.dp)) }
            IconButton(onClick = { Music.player?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) } }) { Icon(Icons.Default.FastRewind, "Back 10 s") }
            FilledIconButton(onClick = { Music.player?.let { if (it.isPlaying) it.pause() else it.play() } }, Modifier.size(84.dp)) {
                Icon(if (Music.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play / pause", Modifier.size(48.dp))
            }
            IconButton(onClick = { Music.player?.let { it.seekTo(it.currentPosition + 10_000L) } }) { Icon(Icons.Default.FastForward, "Forward 10 s") }
            IconButton(onClick = { Music.player?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(32.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVolume) { Icon(Icons.Default.VolumeUp, "Volume", tint = dim) }
            IconButton(onClick = onEq) { Icon(Icons.Default.Equalizer, "Equalizer", tint = dim) }
        }
    }
}


/** Small now-playing strip shown above the tabs on every other page: art, title, love, previous, big play/pause, next. */
@Composable
private fun MiniNow(song: Song, fav: Boolean, onOpen: () -> Unit, onFav: () -> Unit) {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            Music.player?.let { pos = it.currentPosition; dur = it.duration.coerceAtLeast(0L) }
            delay(500)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Column {
            LinearProgressIndicator(
                progress = { if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
                Modifier.fillMaxWidth().height(2.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                    Art(song, Modifier.size(48.dp), 120, 10.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LtrRow {
                IconButton(onClick = onFav) {
                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Music.player?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous") }
                FilledIconButton(onClick = { Music.player?.let { if (it.isPlaying) it.pause() else it.play() } }, Modifier.size(52.dp)) {
                    Icon(if (Music.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play / pause", Modifier.size(30.dp))
                }
                IconButton(onClick = { Music.player?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next") }
                }
            }
        }
    }
}


@Composable
private fun EqualizerDialog(onClose: () -> Unit, onSystem: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Equalizer")) },
        text = {
            if (!Eq.available) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Start a song first, then open the equalizer."))
                    TextButton(onClick = onSystem) { Text(tr("Open the system equalizer")) }
                }
            } else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Equalizer on"), Modifier.weight(1f))
                    Switch(Eq.on, { Eq.turnOn(it) })
                }
                if (Eq.presets.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Eq.presets.size) { i ->
                            FilterChip(Eq.preset == i, { Eq.usePreset(i) }, label = { Text(Eq.presets[i]) })
                        }
                    }
                }
                Eq.levels.forEachIndexed { b, v ->
                    val hz = Eq.freqs.getOrNull(b) ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz", Modifier.width(66.dp), style = MaterialTheme.typography.labelMedium)
                        Slider(v.toFloat(), { Eq.setBand(b, it.roundToInt()) }, Modifier.weight(1f), enabled = Eq.on, valueRange = Eq.minLevel.toFloat()..Eq.maxLevel.toFloat())
                        Text("%+d".format(v / 100), Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Bass boost"), Modifier.width(66.dp), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                    Slider(Eq.bass.toFloat(), { Eq.changeBass(it.roundToInt()) }, Modifier.weight(1f), enabled = Eq.on, valueRange = 0f..1000f)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Done")) } },
        dismissButton = { if (Eq.available) TextButton(onClick = { Eq.reset() }) { Text(tr("Reset")) } }
    )
}

@Composable
private fun LtrRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) = Ltr { Row(modifier, horizontalArrangement, verticalAlignment, content = content) }
