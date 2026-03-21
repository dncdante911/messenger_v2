package com.worldmates.messenger.ui.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.LinkPreviewData
import com.worldmates.messenger.utils.extractYouTubeVideoId
import com.worldmates.messenger.utils.fetchLinkPreview
import com.worldmates.messenger.utils.isYouTubeUrl
import com.worldmates.messenger.utils.youTubeThumbnailUrl

/**
 * Composable that asynchronously loads and displays a link preview.
 * For YouTube URLs, shows a video card with thumbnail + inline WebView player on tap.
 * For other URLs, shows an Open Graph card (title, description, image).
 *
 * Call this from inside a message bubble, after the text content.
 */
@Composable
fun MessageLinkPreview(
    url: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onLinkClick: (String) -> Unit = {}
) {
    if (isYouTubeUrl(url)) {
        val videoId = remember(url) { extractYouTubeVideoId(url) }
        if (videoId != null) {
            YouTubeVideoCard(
                videoId = videoId,
                url = url,
                accentColor = accentColor,
                onOpenUrl = onLinkClick
            )
        }
        return
    }

    var preview by remember(url) { mutableStateOf<LinkPreviewData?>(null) }
    var loaded by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        preview = fetchLinkPreview(url)
        loaded = true
    }

    AnimatedVisibility(
        visible = loaded && preview != null,
        enter = expandVertically() + fadeIn()
    ) {
        preview?.let { data ->
            OgLinkPreviewCard(
                data = data,
                accentColor = accentColor,
                onClick = { onLinkClick(url) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Open Graph Preview Card
// ─────────────────────────────────────────────────────────────

@Composable
private fun OgLinkPreviewCard(
    data: LinkPreviewData,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            // Text content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Site name
                if (!data.siteName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = data.siteName,
                            fontSize = 11.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Title
                if (!data.title.isNullOrBlank()) {
                    Text(
                        text = data.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Description
                if (!data.description.isNullOrBlank()) {
                    Text(
                        text = data.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Thumbnail image (if available)
            if (!data.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = data.imageUrl,
                    contentDescription = data.title,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// YouTube Video Card
// ─────────────────────────────────────────────────────────────

@Composable
fun YouTubeVideoCard(
    videoId: String,
    url: String,
    accentColor: Color = Color(0xFFFF0000),
    onOpenUrl: (String) -> Unit = {}
) {
    val thumbnailUrl = remember(videoId) { youTubeThumbnailUrl(videoId) }

    // Thumbnail card — tapping opens the YouTube app / browser (no WebView embed,
    // because most videos block iframe embedding with error 152).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenUrl(url) },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = stringResource(R.string.cd_youtube_thumbnail),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Dark scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )
        // Play button
        Icon(
            imageVector = Icons.Default.PlayCircleFilled,
            contentDescription = stringResource(R.string.cd_play),
            tint = Color.White,
            modifier = Modifier.size(56.dp)
        )
        // YouTube branding pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFFF0000)
        ) {
            Text(
                text = "YouTube",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

