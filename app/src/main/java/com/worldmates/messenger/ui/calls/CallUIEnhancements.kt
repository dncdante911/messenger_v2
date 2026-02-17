package com.worldmates.messenger.ui.calls

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// ==================== ENHANCED CALL CONTROL BAR ====================

/**
 * Покращена панель керування дзвінком з glassmorphism ефектом
 * Замінює стандартні 2 ряди кнопок
 */
@Composable
fun EnhancedCallControlBar(
    audioEnabled: Boolean,
    videoEnabled: Boolean,
    speakerEnabled: Boolean,
    isScreenSharing: Boolean = false,
    isRecording: Boolean = false,
    noiseCancellation: Boolean = true,
    onToggleAudio: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleScreenShare: () -> Unit = {},
    onToggleRecording: () -> Unit = {},
    onToggleNoiseCancellation: () -> Unit = {},
    onEndCall: () -> Unit,
    onPiP: () -> Unit = {},
    onShowReactions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: Main controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xCC1E1E1E))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCallButton(
                icon = if (audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                label = if (audioEnabled) "Мік" else "Вимк",
                isActive = audioEnabled,
                activeColor = Color(0xFF2196F3),
                onClick = onToggleAudio
            )

            GlassCallButton(
                icon = if (videoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = if (videoEnabled) "Відео" else "Вимк",
                isActive = videoEnabled,
                activeColor = Color(0xFF2196F3),
                onClick = onToggleVideo
            )

            GlassCallButton(
                icon = if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                label = "Динамік",
                isActive = speakerEnabled,
                activeColor = Color(0xFF4CAF50),
                onClick = onToggleSpeaker
            )

            GlassCallButton(
                icon = Icons.Default.Cameraswitch,
                label = "Камера",
                isActive = false,
                onClick = onSwitchCamera
            )

            // End call (large red button)
            EndCallButton(onClick = onEndCall)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Advanced features
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x991E1E1E))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallFeatureButton(
                icon = Icons.Default.ScreenShare,
                label = "Екран",
                isActive = isScreenSharing,
                activeColor = Color(0xFF00BCD4),
                onClick = onToggleScreenShare
            )

            SmallFeatureButton(
                icon = Icons.Default.FiberManualRecord,
                label = "Запис",
                isActive = isRecording,
                activeColor = Color(0xFFE53935),
                onClick = onToggleRecording
            )

            SmallFeatureButton(
                icon = Icons.Default.GraphicEq,
                label = "Шум",
                isActive = noiseCancellation,
                activeColor = Color(0xFF9C27B0),
                onClick = onToggleNoiseCancellation
            )

            SmallFeatureButton(
                icon = Icons.Default.EmojiEmotions,
                label = "Реакції",
                isActive = false,
                activeColor = Color(0xFFFF9800),
                onClick = onShowReactions
            )

            SmallFeatureButton(
                icon = Icons.Default.PictureInPicture,
                label = "PiP",
                isActive = false,
                activeColor = Color(0xFF00BCD4),
                onClick = onPiP
            )
        }
    }
}

/**
 * Кнопка керування з glassmorphism ефектом
 */
@Composable
fun GlassCallButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color(0xFF2196F3),
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.9f)
                    else Color(0xFF3A3A3A)
                )
                .then(
                    if (isActive) Modifier.border(1.dp, activeColor.copy(alpha = 0.5f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) activeColor else Color(0xFFAAAAAA),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Кнопка завершення дзвінка
 */
@Composable
fun EndCallButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "end_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size((56 * scale).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFF1744), Color(0xFFD50000))
                    )
                )
                .shadow(4.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "Завершити",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Кінець",
            fontSize = 10.sp,
            color = Color(0xFFFF5252),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Маленька кнопка додаткових функцій
 */
@Composable
fun SmallFeatureButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color(0xFF2196F3),
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.25f)
                    else Color.Transparent
                )
                .then(
                    if (isActive) Modifier.border(1.dp, activeColor.copy(alpha = 0.6f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color(0xFF888888),
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = label,
            fontSize = 9.sp,
            color = if (isActive) activeColor else Color(0xFF888888),
            textAlign = TextAlign.Center
        )
    }
}

// ==================== ENHANCED CONNECTION STATUS ====================

/**
 * Покращений індикатор стану з'єднання
 */
@Composable
fun EnhancedConnectionStatus(
    connectionState: String,
    callDuration: Int,
    calleeName: String,
    isRecording: Boolean = false,
    isScreenSharing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val statusColor = when (connectionState) {
        "CONNECTED" -> Color(0xFF4CAF50)
        "CONNECTING", "ACCEPTING" -> Color(0xFFFFC107)
        "RECONNECTING" -> Color(0xFFFF9800)
        else -> Color(0xFF888888)
    }

    // Pulse animation for connecting
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    statusColor.copy(
                        alpha = if (connectionState == "CONNECTED") 1f else dotAlpha
                    )
                )
        )

        // Duration or status text
        if (connectionState == "CONNECTED" && callDuration > 0) {
            Text(
                text = formatDuration(callDuration),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        } else {
            Text(
                text = when (connectionState) {
                    "CONNECTING" -> "З'єднання..."
                    "ACCEPTING" -> "Прийняття..."
                    "RECONNECTING" -> "Перепідключення..."
                    "CONNECTED" -> calleeName
                    else -> connectionState
                },
                fontSize = 14.sp,
                color = Color(0xFFCCCCCC)
            )
        }

        // Recording indicator
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935).copy(alpha = dotAlpha))
            )
            Text(
                text = "REC",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935)
            )
        }

        // Screen sharing indicator
        if (isScreenSharing) {
            Icon(
                imageVector = Icons.Default.ScreenShare,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF00BCD4)
            )
        }
    }
}

// ==================== ENHANCED OUTGOING CALL ====================

/**
 * Покращений екран вихідного дзвінка з красивими анімаціями
 */
@Composable
fun EnhancedOutgoingCallScreen(
    calleeName: String,
    calleeAvatar: String,
    callType: String,
    onCancel: () -> Unit
) {
    // Pulsating rings
    val infiniteTransition = rememberInfiniteTransition(label = "calling")

    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring1"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring1alpha"
    )

    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring2"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring2alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B2838), Color(0xFF0D1B2A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with pulsating rings
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Ring 1
                Box(
                    modifier = Modifier
                        .size((140 * ring1).dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            Color(0xFF2196F3).copy(alpha = ring1Alpha),
                            CircleShape
                        )
                )

                // Ring 2
                Box(
                    modifier = Modifier
                        .size((140 * ring2).dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            Color(0xFF2196F3).copy(alpha = ring2Alpha),
                            CircleShape
                        )
                )

                // Avatar
                if (calleeAvatar.isNotEmpty()) {
                    AsyncImage(
                        model = calleeAvatar,
                        contentDescription = calleeName,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color(0xFF2196F3).copy(alpha = 0.5f), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A3A4A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = calleeName.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = calleeName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (callType == "video") "Відеодзвінок..." else "Дзвонимо...",
                fontSize = 16.sp,
                color = Color(0xFF8899AA)
            )

            Spacer(modifier = Modifier.height(80.dp))

            // Cancel button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD32F2F))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Скасувати",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Скасувати",
                fontSize = 14.sp,
                color = Color(0xFF8899AA)
            )
        }
    }
}

// ==================== RECORDING NOTIFICATION ====================

/**
 * Сповіщення про запис дзвінка (показується всім учасникам)
 */
@Composable
fun RecordingNotificationBanner(
    recordedBy: String = "Ви",
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE53935).copy(alpha = 0.15f))
            .border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935).copy(alpha = alpha))
        )

        Text(
            text = "$recordedBy записує цей дзвінок",
            fontSize = 13.sp,
            color = Color(0xFFE53935),
            modifier = Modifier.weight(1f)
        )

        if (onStop != null) {
            TextButton(
                onClick = onStop,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = "Зупинити",
                    fontSize = 12.sp,
                    color = Color(0xFFE53935),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==================== SCREEN SHARING OVERLAY ====================

/**
 * Індикатор що екран демонструється
 */
@Composable
fun ScreenSharingBanner(
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF00BCD4).copy(alpha = 0.15f))
            .border(1.dp, Color(0xFF00BCD4).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color(0xFF00BCD4)
        )

        Text(
            text = "Ви демонструєте екран",
            fontSize = 13.sp,
            color = Color(0xFF00BCD4),
            modifier = Modifier.weight(1f)
        )

        TextButton(
            onClick = onStop,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = "Зупинити",
                fontSize = 12.sp,
                color = Color(0xFF00BCD4),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== NOISE CANCELLATION INDICATOR ====================

/**
 * Індикатор шумозаглушення
 */
@Composable
fun NoiseCancellationIndicator(
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isEnabled,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF9C27B0).copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color(0xFF9C27B0)
            )
            Text(
                text = "AI шумозаглушення",
                fontSize = 10.sp,
                color = Color(0xFF9C27B0)
            )
        }
    }
}

// ==================== PIP MODE HELPER ====================

/**
 * Увійти в режим Picture-in-Picture
 */
fun enterPiPMode(context: Context) {
    if (context is Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .build()
        context.enterPictureInPictureMode(params)
    }
}

// ==================== CALL AUDIO MODE BADGE ====================

/**
 * Індикатор аудіо режиму (динамік, навушники, bluetooth)
 */
@Composable
fun AudioModeBadge(
    speakerEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val icon = when {
        speakerEnabled -> Icons.Default.VolumeUp
        else -> Icons.Default.VolumeDown
    }
    val label = when {
        speakerEnabled -> "Динамік"
        else -> "Навушник"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = Color(0xFFCCCCCC)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFFCCCCCC)
        )
    }
}
