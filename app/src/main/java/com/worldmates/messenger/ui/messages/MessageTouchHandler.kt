package com.worldmates.messenger.ui.messages

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Improved Message Touch Handler
 *
 * Fixes UX issues with message selection:
 * 1. Larger touch target for easier selection
 * 2. Better distinction between tap, long press, and swipe
 * 3. Visual feedback during touch
 * 4. Haptic feedback for selection
 *
 * Touch behaviors:
 * - Single tap: Select/deselect in selection mode, otherwise no action
 * - Long press: Enter selection mode and select message
 * - Double tap: Quick reaction (❤️)
 * - Swipe right: Reply to message
 */

// ==================== TOUCH STATE ====================

data class MessageTouchState(
    val offsetX: Float = 0f,
    val isPressed: Boolean = false,
    val touchDownTime: Long = 0,
    val touchStartPosition: Offset = Offset.Zero
)

// ==================== TOUCH CONFIG ====================

object MessageTouchConfig {
    const val LONG_PRESS_TIMEOUT_MS = 400L
    const val TAP_TIMEOUT_MS = 200L
    const val DOUBLE_TAP_TIMEOUT_MS = 300L
    const val MAX_TAP_DISTANCE_PX = 30f
    const val SWIPE_THRESHOLD = 60f
    const val MAX_SWIPE_DISTANCE = 120f
    const val SELECTION_SCALE = 0.98f
}

// ==================== COMPOSABLE ====================

/**
 * Wrapper for message content with improved touch handling
 */
@Composable
fun MessageTouchWrapper(
    messageId: Long,
    isOwn: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeReply: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme

    var touchState by remember { mutableStateOf(MessageTouchState()) }
    var lastTapTime by remember { mutableStateOf(0L) }

    // Animation for selection feedback
    val scale by animateFloatAsState(
        targetValue = if (touchState.isPressed) MessageTouchConfig.SELECTION_SCALE else 1f,
        animationSpec = tween(100),
        label = "message_scale"
    )

    // Animation for swipe offset
    val animatedOffsetX by animateFloatAsState(
        targetValue = touchState.offsetX,
        animationSpec = tween(if (touchState.offsetX == 0f) 200 else 0),
        label = "swipe_offset"
    )

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Reply indicator (shows during swipe)
        if (animatedOffsetX > 20f) {
            Box(
                modifier = Modifier
                    .align(if (isOwn) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 16.dp)
            ) {
                val replyAlpha = (animatedOffsetX / MessageTouchConfig.MAX_SWIPE_DISTANCE)
                    .coerceIn(0f, 1f)
                val replyScale = 0.5f + (replyAlpha * 0.5f)

                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = "Reply",
                    tint = colorScheme.primary.copy(alpha = replyAlpha),
                    modifier = Modifier
                        .size(28.dp)
                        .scale(replyScale)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .scale(scale)
                .pointerInput(messageId, isSelectionMode) {
                    coroutineScope {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downTime = System.currentTimeMillis()
                                val startPosition = down.position

                                touchState = touchState.copy(
                                    isPressed = true,
                                    touchDownTime = downTime,
                                    touchStartPosition = startPosition
                                )

                                var event: PointerEvent
                                var isLongPressed = false
                                var isDragging = false

                                // Launch long press detection
                                val longPressJob = launch {
                                    kotlinx.coroutines.delay(MessageTouchConfig.LONG_PRESS_TIMEOUT_MS)
                                    if (!isDragging && touchState.isPressed) {
                                        isLongPressed = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                        if (!isSelectionMode) {
                                            onLongPress()
                                        }
                                        onToggleSelection()
                                    }
                                }

                                try {
                                    do {
                                        event = awaitPointerEvent()
                                        val currentPosition = event.changes.firstOrNull()?.position
                                            ?: startPosition

                                        val dragDistance = currentPosition.x - startPosition.x
                                        val verticalDrag = abs(currentPosition.y - startPosition.y)

                                        // Check if this is horizontal drag (swipe for reply)
                                        if (!isDragging &&
                                            abs(dragDistance) > MessageTouchConfig.MAX_TAP_DISTANCE_PX &&
                                            abs(dragDistance) > verticalDrag * 2
                                        ) {
                                            isDragging = true
                                            longPressJob.cancel()
                                        }

                                        if (isDragging) {
                                            // Handle horizontal swipe
                                            touchState = touchState.copy(
                                                offsetX = dragDistance
                                                    .coerceIn(0f, MessageTouchConfig.MAX_SWIPE_DISTANCE)
                                            )
                                        }

                                    } while (event.changes.any { it.pressed })
                                } finally {
                                    longPressJob.cancel()
                                }

                                val upTime = System.currentTimeMillis()
                                val pressDuration = upTime - downTime

                                // Reset state
                                touchState = touchState.copy(isPressed = false)

                                // Handle gesture completion
                                when {
                                    // Swipe completed - check if should trigger reply
                                    isDragging -> {
                                        if (touchState.offsetX > MessageTouchConfig.SWIPE_THRESHOLD) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSwipeReply()
                                        }
                                        touchState = touchState.copy(offsetX = 0f)
                                    }

                                    // Long press already handled above
                                    isLongPressed -> {
                                        // Already handled
                                    }

                                    // Check for tap/double tap
                                    pressDuration < MessageTouchConfig.LONG_PRESS_TIMEOUT_MS -> {
                                        val timeSinceLastTap = upTime - lastTapTime

                                        if (timeSinceLastTap < MessageTouchConfig.DOUBLE_TAP_TIMEOUT_MS) {
                                            // Double tap detected
                                            if (!isSelectionMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onDoubleTap()
                                            }
                                            lastTapTime = 0L
                                        } else {
                                            // Single tap
                                            lastTapTime = upTime

                                            if (isSelectionMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onToggleSelection()
                                            } else {
                                                // Wait a bit to check for double tap
                                                launch {
                                                    kotlinx.coroutines.delay(MessageTouchConfig.DOUBLE_TAP_TIMEOUT_MS)
                                                    if (lastTapTime == upTime) {
                                                        // No double tap occurred, trigger single tap
                                                        onTap()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox (visible in selection mode)
            if (isSelectionMode) {
                SelectionCheckbox(
                    isSelected = isSelected,
                    modifier = Modifier.padding(end = if (!isOwn) 8.dp else 0.dp)
                )
            }

            // Message content
            content()

            // Selection checkbox on the right for own messages
            if (isSelectionMode && isOwn) {
                Spacer(modifier = Modifier.width(8.dp))
                SelectionCheckbox(isSelected = isSelected)
            }
        }
    }
}

/**
 * Selection checkbox with animation
 */
@Composable
private fun SelectionCheckbox(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = tween(150),
        label = "checkbox_scale"
    )

    Box(
        modifier = modifier
            .size(28.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isSelected) colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Circle,
            contentDescription = if (isSelected) "Вибрано" else "Не вибрано",
            tint = if (isSelected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp)
        )
    }
}

// ==================== TOUCH AREA HELPER ====================

/**
 * Extends the touch area of a composable for easier interaction
 */
@Composable
fun ExtendedTouchArea(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    minTouchTarget: Int = 48,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minTouchTarget.dp, minHeight = minTouchTarget.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
