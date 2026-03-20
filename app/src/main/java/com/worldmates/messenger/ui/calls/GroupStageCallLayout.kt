package com.worldmates.messenger.ui.calls

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R

/**
 * GroupStageCallLayout — stage-style layout для групового дзвінка.
 *
 * Структура:
 *  ┌─────────────────────────────┐
 *  │  Організатор (завжди вгорі) │  ← велика плитка з відео/аватаром
 *  ├─────────────────────────────┤
 *  │  Говорять (до 3 відео-карт) │  ← з'являються тільки якщо є потік
 *  ├─────────────────────────────┤
 *  │  Слухачі (список рядків)    │  ← аватар + ім'я + індикатор мікрофона
 *  └─────────────────────────────┘
 *
 * Ліміти учасників: 5 (Free) / 35 (Premium).
 *
 * @param creator          Учасник-організатор (завжди показується)
 * @param localParticipant Локальний учасник (може збігатися з creator)
 * @param participants     Всі інші учасники (без creator)
 * @param isCreatorLocal   true, якщо поточний користувач є організатором
 * @param modifier         Modifier для зовнішнього контейнера
 */
@Composable
fun GroupStageCallLayout(
    creator: GroupCallParticipant,
    localParticipant: GroupCallParticipant?,
    participants: List<GroupCallParticipant>,
    isCreatorLocal: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Активні спікери: учасники (не creator), які говорять і мають відеопотік
    val activeSpeakers = remember(participants) {
        participants.filter { p ->
            p.isSpeaking && p.videoEnabled && p.mediaStream != null
        }.take(3)
    }

    // Слухачі: всі інші учасники (не creator, не активні спікери з відео)
    val activeSpeakerIds = activeSpeakers.map { it.userId }.toSet()
    val listeners = remember(participants, activeSpeakerIds) {
        participants.filter { p -> p.userId !in activeSpeakerIds }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── CREATOR TILE ──────────────────────────────────────────────────────
        item {
            StageSectionLabel(text = stringResource(R.string.stage_call_creator_label))
            Spacer(modifier = Modifier.height(6.dp))
            StageCreatorTile(
                participant = creator,
                isLocal = isCreatorLocal
            )
        }

        // ── ACTIVE SPEAKERS ROW (appears only when speaking + video stream) ──
        if (activeSpeakers.isNotEmpty()) {
            item {
                StageSectionLabel(text = stringResource(R.string.stage_call_speakers_section))
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeSpeakers, key = { it.userId }) { speaker ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            StageSpeakerCard(
                                participant = speaker,
                                isLocal = speaker.userId == localParticipant?.userId
                            )
                        }
                    }
                }
            }
        }

        // ── LISTENERS LIST ────────────────────────────────────────────────────
        if (listeners.isNotEmpty()) {
            item {
                StageSectionLabel(
                    text = stringResource(R.string.stage_call_listeners_section) +
                            " (${listeners.size})"
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(listeners, key = { it.userId }) { listener ->
                StageListenerRow(
                    participant = listener,
                    isLocal = listener.userId == localParticipant?.userId
                )
            }
        }
    }
}

// ==================== CREATOR TILE ====================

/**
 * Велика плитка організатора — завжди видима вгорі.
 * Показує відео якщо є потік, інакше — великий аватар.
 */
@Composable
private fun StageCreatorTile(
    participant: GroupCallParticipant,
    isLocal: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C2E))
            .border(
                width = if (participant.isSpeaking) 2.dp else 1.dp,
                color = if (participant.isSpeaking) Color(0xFF4CAF50) else Color(0xFF2A2A40),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        if (participant.videoEnabled && participant.mediaStream != null) {
            ParticipantVideoView(
                stream = participant.mediaStream,
                isMirrored = isLocal,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Аватар по центру
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StageAvatar(participant = participant, size = 80)
            }
        }

        // Name + mic overlay
        StageNameBadge(
            name = if (isLocal) stringResource(R.string.stage_call_you_label) else participant.name,
            audioEnabled = participant.audioEnabled,
            isSpeaking = participant.isSpeaking,
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // Organizational crown icon
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFFFD700).copy(alpha = 0.9f)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.stage_call_creator_label),
                tint = Color.Black,
                modifier = Modifier
                    .padding(4.dp)
                    .size(14.dp)
            )
        }

        // Speaking indicator
        if (participant.isSpeaking) {
            SpeakingIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            )
        }
    }
}

// ==================== SPEAKER CARD ====================

/**
 * Відео-картка активного спікера (160×200 dp).
 * З'являється тільки коли учасник говорить і транслює відео.
 */
@Composable
private fun StageSpeakerCard(
    participant: GroupCallParticipant,
    isLocal: Boolean
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A2E))
            .border(
                width = 2.dp,
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        // Video stream (only shown when actively speaking with video)
        ParticipantVideoView(
            stream = participant.mediaStream!!,
            isMirrored = isLocal,
            modifier = Modifier.fillMaxSize()
        )

        StageNameBadge(
            name = if (isLocal) stringResource(R.string.stage_call_you_label) else participant.name,
            audioEnabled = participant.audioEnabled,
            isSpeaking = true,
            modifier = Modifier.align(Alignment.BottomStart)
        )

        SpeakingIndicator(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}

// ==================== LISTENER ROW ====================

/**
 * Компактний рядок слухача у списку.
 * Аватар + ім'я + індикатор стану мікрофона.
 */
@Composable
private fun StageListenerRow(
    participant: GroupCallParticipant,
    isLocal: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF161622))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StageAvatar(participant = participant, size = 38)

        Text(
            text = if (isLocal) stringResource(R.string.stage_call_you_label) else participant.name,
            fontSize = 14.sp,
            fontWeight = if (isLocal) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isLocal) Color(0xFF90CAF9) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Connection state indicator
        when (participant.connectionState) {
            "connecting" -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = Color(0xFFFFC107)
            )
            "disconnected" -> Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(16.dp)
            )
        }

        // Mic status
        Icon(
            imageVector = if (participant.audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = if (participant.audioEnabled) null
            else stringResource(R.string.stage_call_muted),
            tint = if (participant.audioEnabled) Color(0xFF66BB6A) else Color(0xFFFF5252),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ==================== SHARED HELPERS ====================

@Composable
private fun StageSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF8899AA),
        letterSpacing = 1.sp
    )
}

@Composable
private fun StageAvatar(participant: GroupCallParticipant, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFF2A3A4A))
            .border(
                width = if (participant.isSpeaking) 2.dp else 0.dp,
                color = if (participant.isSpeaking) Color(0xFF4CAF50) else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (participant.avatar != null) {
            AsyncImage(
                model = participant.avatar,
                contentDescription = participant.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = participant.name.firstOrNull()?.uppercase() ?: "?",
                fontSize = (size / 2.5).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun StageNameBadge(
    name: String,
    audioEnabled: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x99000000))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = if (audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = null,
            tint = if (!audioEnabled) Color(0xFFFF5252)
                   else if (isSpeaking) Color(0xFF4CAF50)
                   else Color.White,
            modifier = Modifier.size(14.dp)
        )
    }
}
