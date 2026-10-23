package com.myvolumecontrol

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

@Composable
internal fun ProfileList(
    app: VolumeApplication,
    activeId: String?,
    scrollState: ScrollState,
    viewportBounds: Rect,
    onEdit: (SoundProfile) -> Unit,
    onDelete: (SoundProfile) -> Unit,
) {
    val profiles = app.profiles
    var ordered by remember(profiles) { mutableStateOf(profiles) }
    var draggingId by remember(profiles) { mutableStateOf<String?>(null) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val viewport by rememberUpdatedState(viewportBounds)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val speed = with(LocalDensity.current) { 600.dp.toPx() }
    val haptics = LocalHapticFeedback.current

    fun moveDragged() {
        val id = draggingId ?: return
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0) return
        val target = ordered.count { profile ->
            profile.id != id && (bounds[profile.id]?.center?.y ?: Float.MAX_VALUE) < dragCenter
        }
        if (target != from) ordered = moveProfile(ordered, id, target)
    }

    fun saveOrder() {
        if (!app.reorderProfiles(ordered.map { it.id })) ordered = profiles
    }

    fun moveBy(id: String, direction: Int): Boolean {
        val from = ordered.indexOfFirst { it.id == id }
        val target = from + direction
        if (from < 0 || target !in ordered.indices) return false
        ordered = moveProfile(ordered, id, target)
        val saved = app.reorderProfiles(ordered.map { it.id })
        if (!saved) ordered = profiles
        return saved
    }

    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (coroutineContext.isActive) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
            previous = now
            val direction = when {
                viewport.height <= 0 -> 0f
                dragCenter < viewport.top + edge -> -1f
                dragCenter > viewport.bottom - edge -> 1f
                else -> 0f
            }
            if (direction != 0f) scrollState.scrollBy(direction * speed * seconds)
            moveDragged()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ordered.forEachIndexed { index, profile ->
            key(profile.id) {
                val dragging = draggingId == profile.id
                Box(
                    Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
                        .onGloballyPositioned {
                            val position = it.positionInRoot()
                            bounds[profile.id] = Rect(
                                position.x, position.y,
                                position.x + it.size.width, position.y + it.size.height,
                            )
                        },
                ) {
                    val handle = Modifier
                        .semantics {
                            contentDescription = "Reorder ${profile.name}"
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction("Move up") { moveBy(profile.id, -1) })
                                if (index < ordered.lastIndex) add(CustomAccessibilityAction("Move down") { moveBy(profile.id, 1) })
                            }
                        }
                        .pointerInput(profile.id, profiles) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    bounds[profile.id]?.let { rect ->
                                        dragCenter = rect.center.y
                                        draggingId = profile.id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragCenter += amount.y
                                    moveDragged()
                                },
                                onDragEnd = {
                                    draggingId = null
                                    saveOrder()
                                },
                                onDragCancel = {
                                    draggingId = null
                                    ordered = profiles
                                },
                            )
                        }
                    ProfileCard(
                        profile = profile,
                        active = activeId == profile.id,
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            translationY = if (dragging) dragCenter - (bounds[profile.id]?.center?.y ?: dragCenter) else 0f
                            shadowElevation = if (dragging) 8.dp.toPx() else 0f
                        },
                        dragHandle = handle,
                        onApply = { app.applyProfile(profile.id) },
                        onPin = { app.togglePin(profile.id) },
                        onEdit = { onEdit(profile) },
                        onDelete = { onDelete(profile) },
                        canMoveUp = index > 0,
                        canMoveDown = index < ordered.lastIndex,
                        onMoveUp = { moveBy(profile.id, -1) },
                        onMoveDown = { moveBy(profile.id, 1) },
                    )
                }
            }
        }
    }
}
