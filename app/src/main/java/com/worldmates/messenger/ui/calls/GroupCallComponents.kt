package com.worldmates.messenger.ui.calls

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.worldmates.messenger.network.WebRTCManager
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer

/**
 * Модель учасника групового дзвінка
 */
data class GroupCallParticipant(
    val userId: Long,
    val name: String,
    val avatar: String? = null,
    val audioEnabled: Boolean = true,
    val videoEnabled: Boolean = false,
    val isSpeaking: Boolean = false,
    val mediaStream: MediaStream? = null,
    val connectionState: String = "connected" // connected, connecting, disconnected
)

// ==================== GROUP CALL GRID ====================

/**
 * Сітка учасників групового дзвінка
 * Адаптивний layout: 1-4 учасники різні конфігурації
 */
@Composable
fun GroupCallGrid(
    participants: List<GroupCallParticipant>,
    localParticipant: GroupCallParticipant? = null,
    modifier: Modifier = Modifier
) {
    val allParticipants = listOfNotNull(localParticipant) + participants

    when (allParticipants.size) {
        0 -> { /* Empty state */ }
        1 -> {
            // Один учасник - повноекранний
            Box(modifier = modifier.fillMaxSize()) {
                ParticipantTile(
                    participant = allParticipants[0],
                    isLocal = allParticipants[0] == localParticipant,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        2 -> {
            // Два учасники - поділений екран по вертикалі
            Column(modifier = modifier.fillMaxSize()) {
                allParticipants.forEach { participant ->
                    ParticipantTile(
                        participant = participant,
                        isLocal = participant == localParticipant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
        3, 4 -> {
            // 3-4 учасники - 2x2 сітка
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(allParticipants) { participant ->
                    ParticipantTile(
                        participant = participant,
                        isLocal = participant == localParticipant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                    )
                }
            }
        }
        else -> {
            // 5+ учасників - адаптивна сітка 3 колонки
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(allParticipants) { participant ->
                    ParticipantTile(
                        participant = participant,
                        isLocal = participant == localParticipant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                    )
                }
            }
        }
    }
}

// ==================== PARTICIPANT TILE ====================

/**
 * Плитка одного учасника
 */
@Composable
fun ParticipantTile(
    participant: GroupCallParticipant,
    isLocal: Boolean = false,
    modifier: Modifier = Modifier
) {
    val speakingBorderColor = if (participant.isSpeaking) Color(0xFF4CAF50) else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .border(
                width = if (participant.isSpeaking) 2.dp else 0.dp,
                color = speakingBorderColor,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        if (participant.videoEnabled && participant.mediaStream != null) {
            // Відео учасника
            ParticipantVideoView(
                stream = participant.mediaStream,
                isMirrored = isLocal,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Аватар без відео
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (participant.avatar != null) {
                    AsyncImage(
                        model = participant.avatar,
                        contentDescription = participant.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A3A4A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = participant.name.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Overlay з інформацією
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isLocal) "Ви" else participant.name,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Mic status
            if (!participant.audioEnabled) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFFF5252)
                )
            }

            // Connection status
            if (participant.connectionState == "connecting") {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.dp,
                    color = Color(0xFFFFC107)
                )
            }
        }

        // Speaking indicator
        if (participant.isSpeaking) {
            SpeakingIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }

        // Local badge
        if (isLocal) {
            Text(
                text = "Ви",
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2196F3))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Відео учасника (SurfaceViewRenderer)
 */
@Composable
fun ParticipantVideoView(
    stream: MediaStream,
    isMirrored: Boolean = false,
    modifier: Modifier = Modifier
) {
    var currentVideoTrack by remember { mutableStateOf<org.webrtc.VideoTrack?>(null) }

    AndroidView(
        factory = { androidContext ->
            SurfaceViewRenderer(androidContext).apply {
                init(WebRTCManager.getEglContext(), null)
                setZOrderMediaOverlay(false)
                setEnableHardwareScaler(true)
                setMirror(isMirrored)
            }
        },
        update = { renderer ->
            val newTrack = if (stream.videoTracks.isNotEmpty()) stream.videoTracks[0] else null
            if (newTrack != currentVideoTrack) {
                currentVideoTrack?.removeSink(renderer)
                newTrack?.addSink(renderer)
                currentVideoTrack = newTrack
            }
        },
        modifier = modifier
    )
}

/**
 * Анімований індикатор що учасник говорить
 */
@Composable
fun SpeakingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking_scale"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF4CAF50).copy(alpha = 0.8f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val barHeight = when (index) {
                0 -> 6.dp * scale
                1 -> 10.dp * scale
                2 -> 4.dp * scale
                else -> 6.dp
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White)
            )
        }
    }
}

// ==================== GROUP CALL HEADER ====================

/**
 * Заголовок групового дзвінка
 */
@Composable
fun GroupCallHeader(
    groupName: String,
    participantCount: Int,
    maxParticipants: Int = 20,
    callDuration: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = groupName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$participantCount / $maxParticipants учасників",
                fontSize = 12.sp,
                color = Color(0xFF8899AA)
            )
        }

        Text(
            text = formatDuration(callDuration),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

// ==================== ADD PARTICIPANT DIALOG ====================

/**
 * Плитка "Додати учасника" в сітці
 */
@Composable
fun AddParticipantTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFF2196F3).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Додати",
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Додати",
                fontSize = 12.sp,
                color = Color(0xFF2196F3)
            )
        }
    }
}
