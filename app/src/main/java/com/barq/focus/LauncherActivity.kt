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

package com.barq.focus

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.barq.focus.data.AppInfo
import com.barq.focus.data.Apps
import com.barq.focus.data.CardType
import com.barq.focus.data.I18n
import com.barq.focus.data.Store
import com.barq.focus.data.clockText
import com.barq.focus.data.dayItems
import com.barq.focus.data.tr
import com.barq.focus.data.trf
import com.barq.focus.service.Notifier
import com.barq.focus.service.TimerAlarm
import com.barq.focus.ui.theme.FocusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Real Android Home Launcher built into Focus: sits transparently over your live/static wallpaper,
 * provides a customizable pinned app grid, bottom dock, searchable slide-up app drawer, live study timer
 * & plate widget, and strict Allowed-Apps-Only Study Lock enforcement.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        I18n.init(this)
        Store(this).let {
            com.barq.focus.ui.theme.AppTheme.accent = it.accent
            com.barq.focus.ui.theme.AppTheme.mode = it.themeMode
            com.barq.focus.ui.theme.AppTheme.checkShape = it.checkShape
            com.barq.focus.ui.theme.AppTheme.loadSaved(it.themeJson)
        }
        com.barq.focus.ui.theme.AppFont.load(this)
        setContent {
            FocusTheme {
                val dir = if (I18n.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent) { LauncherHome() }
                }
            }
        }
    }
}

private const val UNLOCK_PHRASE = "I choose to stop studying now."

private object LauncherIconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun get(ctx: Context, pkg: String): ImageBitmap? {
        cache[pkg]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val d = ctx.packageManager.getApplicationIcon(pkg)
                drawableToBitmap(d).asImageBitmap().also { cache[pkg] = it }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }
        val size = 144
        val w = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(size) ?: size
        val h = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(size) ?: size
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }
}

@Composable
private fun rememberAppIcon(pkg: String): ImageBitmap? {
    val ctx = LocalContext.current
    var icon by remember(pkg) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pkg) {
        icon = LauncherIconCache.get(ctx, pkg)
    }
    return icon
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LauncherHome() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var rev by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { rev++; onPauseOrDispose {} }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    var showDrawer by remember { mutableStateOf(false) }
    var showLock by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (showDrawer) showDrawer = false
    }

    val studyLock = remember(now) { store.studyLock(now) }
    val onlyAllowed = store.launcherOnlyAllowed || studyLock != null
    val allLaunchables = remember(rev) { Apps.launchables(ctx) }
    val pinnedPkgs = remember(rev) { store.launcherApps() }
    val homeApps = remember(allLaunchables, pinnedPkgs) {
        val byPkg = allLaunchables.associateBy { it.pkg }
        pinnedPkgs.mapNotNull { byPkg[it] }.sortedBy { it.label.lowercase() }
    }
    val cards = remember(rev, now / 5000) { store.cards().filter { it.type == CardType.STUDY } }
    val activeCard = cards.firstOrNull { it.id == store.activeCardId }
    val openTasks = remember(cards) { cards.flatMap { c -> c.dayItems.filter { !it.done }.map { c to it } } }

    val dialerPkg = remember(rev) { Apps.resolve(ctx, "phone").firstOrNull() }
    val cameraPkg = remember(rev) { Apps.resolve(ctx, "camera").firstOrNull() }

    fun launch(pkg: String) {
        if (onlyAllowed && pkg !in store.launcherApps() && pkg != ctx.packageName && !Apps.callRelated(pkg)) return
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
            try { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }

    fun openAppInfo(pkg: String) {
        if (studyLock != null) return
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }

    fun togglePin(pkg: String) {
        if (studyLock != null) return
        val cur = store.launcherApps().toMutableSet()
        if (!cur.add(pkg)) cur.remove(pkg)
        store.setLauncherApps(cur)
        rev++
    }

    fun openFocus(go: String? = null) {
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .also { if (go != null) it.putExtra("go", go) }
        )
    }

    val scrim = remember {
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.34f),
            0.45f to Color.Black.copy(alpha = 0.24f),
            1.0f to Color.Black.copy(alpha = 0.62f),
        )
    }
    val labelShadow = remember {
        TextStyle(
            color = Color.White,
            shadow = Shadow(color = Color.Black.copy(alpha = 0.78f), offset = Offset(0f, 2f), blurRadius = 6f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            .pointerInput(onlyAllowed) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dy += dragAmount
                        if (dy < -95f && !onlyAllowed) {
                            showDrawer = true
                            dy = 0f
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            // Clock + Date + Status Pill Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
                        style = labelShadow.copy(fontSize = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp)
                    )
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now)),
                        style = labelShadow.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.88f))
                    )
                }

                // Study lock / guard pill button
                val pillBg = if (studyLock != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                else Color.Black.copy(alpha = 0.45f)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = pillBg,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            when {
                                studyLock == null -> showLock = true
                                store.launcherLockUntil > now && !store.launcherStrict -> showUnlock = true
                            }
                        }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (studyLock != null) Icons.Rounded.Lock else Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        val statusText = when {
                            studyLock != null && store.launcherLockUntil > now ->
                                clockText(((store.launcherLockUntil - now) / 1000L).coerceAtLeast(0L))
                            studyLock != null -> tr("Study lock")
                            onlyAllowed -> tr("Allowed only")
                            else -> tr("Study lock")
                        }
                        Text(statusText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Active Timer & Today's Plate Widget Card
            if (activeCard != null || (store.launcherTasks && openTasks.isNotEmpty())) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF14161B).copy(alpha = 0.78f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        activeCard?.let { c ->
                            val left = if (store.activePaused) store.activeRemaining else store.activeEnd - now
                            val totalMs = (c.minutes.coerceAtLeast(1) * 60_000L).toFloat()
                            val prog = if (left <= 0L) 1f else (1f - left.toFloat() / totalMs).coerceIn(0f, 1f)
                            val accent = Color(c.color)
                            val secs = (abs(left) + 999L) / 1000L

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { openFocus("card:${c.id}") },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Timer, null, tint = accent, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (left < 0) "+" else "") + clockText(secs) + "  ·  " + tr(if (store.activePaused) "Paused" else if (left < 0) "Over budget" else "left"),
                                        color = Color.White.copy(alpha = 0.78f),
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { prog },
                                        color = accent,
                                        trackColor = Color.White.copy(alpha = 0.12f),
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                    )
                                }
                                if (!(store.launcherLockOnTimer && store.launcherStrict)) {
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalIconButton(onClick = {
                                        if (store.activePaused) store.resumeActive() else store.pauseActive()
                                        TimerAlarm.reschedule(ctx, store)
                                        Notifier.syncTimer(ctx, store)
                                        rev++
                                    }) {
                                        Icon(
                                            if (store.activePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                            tr(if (store.activePaused) "Resume" else "Pause")
                                        )
                                    }
                                }
                            }
                        }

                        if (store.launcherTasks && openTasks.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        trf("%d left on your plate", openTasks.size),
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        tr("Focus"),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { openFocus("home") }
                                    )
                                }
                                openTasks.take(3).forEach { (c, it) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { openFocus("card:${c.id}") }
                                            .padding(vertical = 3.dp, horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.RadioButtonUnchecked,
                                            null,
                                            tint = Color(c.color),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            it.text,
                                            color = Color.White.copy(alpha = 0.94f),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Pinned Home Apps Workspace Grid
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (homeApps.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            tr("No apps pinned yet. Tap the Apps icon below or open Study launcher setup."),
                            color = Color.White.copy(alpha = 0.88f),
                            style = labelShadow.copy(fontSize = 14.sp),
                            textAlign = TextAlign.Center
                        )
                        if (studyLock == null) {
                            OutlinedButton(onClick = { openFocus("launcher") }) {
                                Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Choose apps"))
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(store.launcherCols),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(homeApps, key = { it.pkg }) { app ->
                            LauncherAppTile(
                                app = app,
                                iconSize = 54.dp,
                                labelStyle = labelShadow.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                canEdit = studyLock == null,
                                isPinned = true,
                                onLaunch = { launch(app.pkg) },
                                onAppInfo = { openAppInfo(app.pkg) },
                                onTogglePin = { togglePin(app.pkg) }
                            )
                        }
                    }
                }
            }

            // Bottom Floating Dock (Phone · Focus · All Apps Drawer · Camera · Lock)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF14161B).copy(alpha = 0.82f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Phone / Dialer dock slot
                    if (dialerPkg != null) {
                        DockPackageSlot(pkg = dialerPkg, fallback = Icons.Rounded.Phone, label = tr("Phone")) {
                            launch(dialerPkg)
                        }
                    } else {
                        DockVectorSlot(icon = Icons.Rounded.Phone, label = tr("Phone"), tint = Color(0xFF81C784)) {
                            try { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                        }
                    }

                    // Focus main app dock slot
                    DockPackageSlot(pkg = ctx.packageName, fallback = Icons.Rounded.AutoStories, label = tr("Focus")) {
                        openFocus("home")
                    }

                    // App Drawer Button (respects Allowed-Apps-Only!)
                    if (!onlyAllowed) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable { showDrawer = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Apps, tr("All apps"), tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                                .clickable { openFocus("launcher") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Shield, tr("Allowed apps only"), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }

                    // Camera dock slot (only if not blocked or in allowed list)
                    val camAllowed = cameraPkg != null && (!onlyAllowed || cameraPkg in pinnedPkgs)
                    if (camAllowed && cameraPkg != null) {
                        DockPackageSlot(pkg = cameraPkg, fallback = Icons.Rounded.PhotoCamera, label = tr("Camera")) {
                            launch(cameraPkg)
                        }
                    } else {
                        DockVectorSlot(icon = Icons.Rounded.AutoStories, label = tr("Notes"), tint = Color(0xFF90CAF9)) {
                            openFocus("notes")
                        }
                    }

                    // Study Lock / Unlock quick button
                    val lockActive = studyLock != null
                    DockVectorSlot(
                        icon = if (lockActive) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        label = tr(if (lockActive) "Unlock" else "Lock"),
                        tint = if (lockActive) MaterialTheme.colorScheme.primary else Color.White
                    ) {
                        when {
                            studyLock == null -> showLock = true
                            store.launcherLockUntil > now && !store.launcherStrict -> showUnlock = true
                        }
                    }
                }
            }
        }
    }

    // Real Slide-up Searchable App Drawer Sheet (strictly hidden when onlyAllowed is active)
    if (showDrawer && !onlyAllowed) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var query by remember { mutableStateOf("") }
        val filtered = remember(allLaunchables, query) {
            if (query.isBlank()) allLaunchables
            else allLaunchables.filter { it.label.contains(query, ignoreCase = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { showDrawer = false },
            sheetState = sheetState,
            containerColor = Color(0xFF14161B),
            contentColor = Color.White,
            windowInsets = WindowInsets(0)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr("Search"), color = Color.White.copy(alpha = 0.55f)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.7f)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, tr("Clear"), tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(store.launcherCols),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(filtered, key = { it.pkg }) { app ->
                        LauncherAppTile(
                            app = app,
                            iconSize = 52.dp,
                            labelStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            canEdit = true,
                            isPinned = app.pkg in pinnedPkgs,
                            onLaunch = {
                                showDrawer = false
                                launch(app.pkg)
                            },
                            onAppInfo = {
                                showDrawer = false
                                openAppInfo(app.pkg)
                            },
                            onTogglePin = { togglePin(app.pkg) }
                        )
                    }
                }
            }
        }
    }

    if (showLock) {
        AlertDialog(
            onDismissRequest = { showLock = false },
            title = { Text(tr("Study lock")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("Only the apps on this screen, Focus, the phone and the system work. Phone settings can't be opened. It ends by itself; ending it early means typing a sentence."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    listOf(25, 45, 60, 90, 120).forEach { m ->
                        OutlinedButton(
                            onClick = { store.launcherLockUntil = System.currentTimeMillis() + m * 60_000L; showLock = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(trf("%d min", m)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLock = false }) { Text(tr("Cancel")) } }
        )
    }

    if (showUnlock) {
        var typed by remember { mutableStateOf("") }
        val phrase = tr(UNLOCK_PHRASE)
        AlertDialog(
            onDismissRequest = { showUnlock = false },
            title = { Text(tr("Unlock early")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Type this sentence exactly:"), style = MaterialTheme.typography.bodyMedium)
                    Text(phrase, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { next -> if (next.length - typed.length <= 2) typed = next },
                        label = { Text(tr("Type the sentence above, letter by letter")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = typed.trim() == phrase.trim(),
                    onClick = { store.launcherLockUntil = 0L; showUnlock = false }
                ) { Text(tr("Unlock")) }
            },
            dismissButton = { TextButton(onClick = { showUnlock = false }) { Text(tr("Cancel")) } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherAppTile(
    app: AppInfo,
    iconSize: Dp,
    labelStyle: TextStyle,
    canEdit: Boolean,
    isPinned: Boolean,
    onLaunch: () -> Unit,
    onAppInfo: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val bmp = rememberAppIcon(app.pkg)
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onLaunch,
                    onLongClick = { if (canEdit) menuOpen = true else onLaunch() }
                )
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .shadow(4.dp, RoundedCornerShape(16.dp), clip = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Rounded.Apps, app.label, tint = Color.White, modifier = Modifier.size(iconSize * 0.56f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(tr("Open")) },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                onClick = { menuOpen = false; onLaunch() }
            )
            DropdownMenuItem(
                text = { Text(tr(if (isPinned) "Remove from home" else "Pin to home")) },
                leadingIcon = { Icon(if (isPinned) Icons.Rounded.DeleteOutline else Icons.Rounded.Add, null) },
                onClick = { menuOpen = false; onTogglePin() }
            )
            DropdownMenuItem(
                text = { Text(tr("App info")) },
                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                onClick = { menuOpen = false; onAppInfo() }
            )
        }
    }
}

@Composable
private fun DockPackageSlot(pkg: String, fallback: ImageVector, label: String, onClick: () -> Unit) {
    val bmp = rememberAppIcon(pkg)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(fallback, label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun DockVectorSlot(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
    }
}
