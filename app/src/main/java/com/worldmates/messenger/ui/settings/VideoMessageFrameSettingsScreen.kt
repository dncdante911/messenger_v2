package com.worldmates.messenger.ui.settings

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.messages.VideoMessageFrameStyle
import com.worldmates.messenger.ui.messages.getSavedVideoMessageFrameStyle
import com.worldmates.messenger.ui.messages.saveVideoMessageFrameStyle
import kotlinx.coroutines.delay

/**
 * 📹 Екран налаштувань стилю відеоповідомлень
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMessageFrameSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedStyle by remember {
        mutableStateOf(getSavedVideoMessageFrameStyle(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.video_frame_style_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.video_frame_select_desc),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(VideoMessageFrameStyle.entries) { style ->
                VideoMessageStyleCard(
                    style = style,
                    isSelected = style == selectedStyle,
                    onSelect = {
                        selectedStyle = style
                        saveVideoMessageFrameStyle(context, style)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.video_tip_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.video_tip_text),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoMessageStyleCard(
    style: VideoMessageFrameStyle,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Превью рамки
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                VideoMessageStylePreview(style = style)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = style.emoji,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = style.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = localizedVideoFrameDescription(style),
                    fontSize = 14.sp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.done),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun VideoMessageStylePreview(style: VideoMessageFrameStyle) {
    val ovalShape = GenericShape { size, _ -> addOval(Rect(Offset.Zero, size)) }

    // GRADIENT preview uses a portrait (taller-than-wide) box to clearly show the oval shape
    val isOval   = style == VideoMessageFrameStyle.GRADIENT
    val isCircle = style == VideoMessageFrameStyle.CIRCLE

    val containerShape = when {
        isCircle -> CircleShape
        isOval   -> ovalShape
        else     -> RoundedCornerShape(12.dp)
    }
    val previewWidth  = if (isOval) 50.dp else 70.dp
    val previewHeight = if (isOval) 72.dp else 70.dp

    Box(
        modifier = Modifier
            .width(previewWidth)
            .height(previewHeight)
            .clip(containerShape)
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        // Simulated video thumbnail
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(containerShape)
                .background(Color.Gray)
        )

        // Frame border per style
        when (style) {
            VideoMessageFrameStyle.CIRCLE -> {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
            }
            VideoMessageFrameStyle.ROUNDED -> {
                Box(
                    modifier = Modifier
                        .width(previewWidth).height(previewHeight)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                )
            }
            VideoMessageFrameStyle.NEON -> {
                val infiniteTransition = rememberInfiniteTransition(label = "neon")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "glow"
                )
                Box(
                    modifier = Modifier
                        .width(previewWidth).height(previewHeight)
                        .border(2.dp, Color(0xFF00FFFF).copy(alpha = alpha), RoundedCornerShape(12.dp))
                )
            }
            VideoMessageFrameStyle.GRADIENT -> {
                // Oval container with gradient border
                Box(
                    modifier = Modifier
                        .width(previewWidth).height(previewHeight)
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFF667eea), Color(0xFF764ba2), Color(0xFFf093fb))
                            ),
                            ovalShape
                        )
                )
            }
            VideoMessageFrameStyle.RAINBOW -> {
                var offsetX by remember { mutableStateOf(0f) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(50)
                        offsetX = (offsetX + 5f) % 360f
                    }
                }
                Box(
                    modifier = Modifier
                        .width(previewWidth).height(previewHeight)
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFff0000), Color(0xFFff7f00), Color(0xFFffff00),
                                    Color(0xFF00ff00), Color(0xFF0000ff), Color(0xFF9400d3)
                                ),
                                start = Offset(offsetX, 0f),
                                end = Offset(offsetX + 100f, 100f)
                            ),
                            RoundedCornerShape(12.dp)
                        )
                )
            }
            VideoMessageFrameStyle.MINIMAL -> { /* no border */ }
        }
    }
}

@Composable
fun localizedVideoFrameDescription(style: VideoMessageFrameStyle): String {
    return when (style) {
        VideoMessageFrameStyle.CIRCLE -> stringResource(R.string.video_frame_circle_desc)
        VideoMessageFrameStyle.ROUNDED -> stringResource(R.string.video_frame_rounded_desc)
        VideoMessageFrameStyle.NEON -> stringResource(R.string.video_frame_neon_desc)
        VideoMessageFrameStyle.GRADIENT -> stringResource(R.string.video_frame_gradient_desc)
        VideoMessageFrameStyle.RAINBOW -> stringResource(R.string.video_frame_rainbow_desc)
        VideoMessageFrameStyle.MINIMAL -> stringResource(R.string.frame_style_minimal_desc)
    }
}
