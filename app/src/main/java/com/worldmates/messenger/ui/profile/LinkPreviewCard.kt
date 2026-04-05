package com.worldmates.messenger.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.LinkPreviewData

/**
 * Card showing OpenGraph preview for a URL found in a user bio.
 *
 * Shows:
 *   - OG image (if available)        — left or top thumbnail
 *   - title                           — up to 2 lines
 *   - description                     — up to 2 lines, secondary color
 *   - hostname chip                   — bottom-left (e.g. "github.com")
 *   - open-in-browser icon button     — bottom-right
 *
 * Tapping anywhere opens the URL in the system browser.
 */
@Composable
fun LinkPreviewCard(
    data:     LinkPreviewData,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { runCatching { uriHandler.openUri(data.url) } },
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            // ── OG image (top banner, shown only when available) ──────────────
            if (data.image.isNotBlank()) {
                AsyncImage(
                    model             = data.image,
                    contentDescription = data.title.ifBlank { null },
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            }

            // ── Text content ──────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (data.title.isNotBlank()) {
                    Text(
                        text     = data.title,
                        style    = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (data.description.isNotBlank()) {
                    Text(
                        text     = data.description,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // ── Bottom row: hostname chip + open icon ─────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Hostname chip
                    if (data.hostname.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text  = data.hostname,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    // Open-in-browser button
                    IconButton(
                        onClick  = { runCatching { uriHandler.openUri(data.url) } },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = stringResource(R.string.link_preview_open),
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
