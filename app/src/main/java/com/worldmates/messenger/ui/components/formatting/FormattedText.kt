package com.worldmates.messenger.ui.components.formatting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Formatted Text Renderer
 *
 * Renders text with:
 * - @mentions (clickable, highlighted)
 * - #hashtags (clickable, highlighted)
 * - Markdown formatting (bold, italic, code, etc.)
 * - Spoilers (tap to reveal with beautiful animation)
 * - Links (clickable)
 */

// ==================== THEME COLORS ====================

object FormattedTextColors {
    val MentionColor = Color(0xFF2196F3) // Blue
    val HashtagColor = Color(0xFF4CAF50) // Green
    val LinkColor = Color(0xFF1E88E5) // Blue
    val CodeBackground = Color(0x1A000000) // 10% black
    val CodeTextColor = Color(0xFFE91E63) // Pink for code
    val QuoteBarColor = Color(0xFF9E9E9E) // Gray
    val QuoteBackground = Color(0x0D000000) // 5% black
    val SpoilerBackground = Color(0xFF424242) // Dark gray
    val SpoilerRevealedBackground = Color(0x1A000000) // Light when revealed
}

// ==================== MAIN COMPOSABLE ====================

/**
 * Main composable for rendering formatted text
 */
@Composable
fun FormattedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = LocalContentColor.current,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 20.sp,
    settings: FormattingSettings = FormattingSettings(),
    onMentionClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val parsed = remember(text, settings) {
        TextFormattingParser.parse(text, settings)
    }

    // Track revealed spoilers
    val revealedSpoilers = remember { mutableStateMapOf<Int, Boolean>() }

    Column(modifier = modifier) {
        var segmentIndex = 0

        parsed.segments.forEach { segment ->
            when (segment) {
                is FormattedSegment.CodeBlock -> {
                    // Code block - full width with syntax highlighting style
                    CodeBlockView(
                        code = segment.text,
                        language = segment.language,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                is FormattedSegment.Quote -> {
                    // Quote block with vertical bar
                    QuoteView(
                        text = segment.text,
                        textColor = color,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                is FormattedSegment.Spoiler -> {
                    val index = segmentIndex
                    val isRevealed = revealedSpoilers[index] ?: false

                    SpoilerView(
                        text = segment.text,
                        isRevealed = isRevealed,
                        textColor = color,
                        fontSize = fontSize,
                        onToggle = { revealedSpoilers[index] = !isRevealed },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                else -> {
                    // Inline elements - render as annotated string
                    val annotatedString = buildAnnotatedStringForSegment(
                        segment = segment,
                        baseColor = color,
                        fontSize = fontSize
                    )

                    if (annotatedString.text.isNotEmpty()) {
                        ClickableText(
                            text = annotatedString,
                            style = style.copy(
                                color = color,
                                fontSize = fontSize,
                                lineHeight = lineHeight
                            ),
                            onClick = { offset ->
                                // Handle clicks on annotations
                                annotatedString.getStringAnnotations(offset, offset).firstOrNull()?.let { annotation ->
                                    when (annotation.tag) {
                                        "MENTION" -> onMentionClick(annotation.item)
                                        "HASHTAG" -> onHashtagClick(annotation.item)
                                        "URL" -> {
                                            try {
                                                uriHandler.openUri(annotation.item)
                                            } catch (e: Exception) {
                                                onLinkClick(annotation.item)
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            segmentIndex++
        }
    }
}

// ==================== HELPER FUNCTIONS ====================

@Composable
private fun buildAnnotatedStringForSegment(
    segment: FormattedSegment,
    baseColor: Color,
    fontSize: TextUnit
): AnnotatedString {
    return buildAnnotatedString {
        when (segment) {
            is FormattedSegment.Plain -> {
                append(segment.text)
            }

            is FormattedSegment.Bold -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(segment.text)
                }
            }

            is FormattedSegment.Italic -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(segment.text)
                }
            }

            is FormattedSegment.BoldItalic -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(segment.text)
                }
            }

            is FormattedSegment.Code -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = FormattedTextColors.CodeTextColor,
                        background = FormattedTextColors.CodeBackground,
                        fontSize = fontSize * 0.9f
                    )
                ) {
                    append(" ${segment.text} ")
                }
            }

            is FormattedSegment.Strikethrough -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(segment.text)
                }
            }

            is FormattedSegment.Underline -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(segment.text)
                }
            }

            is FormattedSegment.Mention -> {
                pushStringAnnotation(tag = "MENTION", annotation = segment.username)
                withStyle(
                    SpanStyle(
                        color = FormattedTextColors.MentionColor,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append("@${segment.username}")
                }
                pop()
            }

            is FormattedSegment.Hashtag -> {
                pushStringAnnotation(tag = "HASHTAG", annotation = segment.tag)
                withStyle(
                    SpanStyle(
                        color = FormattedTextColors.HashtagColor,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append("#${segment.tag}")
                }
                pop()
            }

            is FormattedSegment.Link -> {
                pushStringAnnotation(tag = "URL", annotation = segment.url)
                withStyle(
                    SpanStyle(
                        color = FormattedTextColors.LinkColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(segment.text)
                }
                pop()
            }

            is FormattedSegment.AutoLink -> {
                pushStringAnnotation(tag = "URL", annotation = segment.url)
                withStyle(
                    SpanStyle(
                        color = FormattedTextColors.LinkColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(segment.url)
                }
                pop()
            }

            // These are handled separately in the Column
            is FormattedSegment.CodeBlock,
            is FormattedSegment.Quote,
            is FormattedSegment.Spoiler -> {
                // No-op, handled in Column
            }
        }
    }
}

// ==================== SPECIAL COMPONENTS ====================

/**
 * Code block with syntax highlighting style
 */
@Composable
fun CodeBlockView(
    code: String,
    language: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E), // Dark background like VS Code
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Language badge (if specified)
            if (!language.isNullOrEmpty()) {
                Text(
                    text = language.uppercase(),
                    color = Color(0xFF9CDCFE), // Light blue
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Code text
            Text(
                text = code,
                color = Color(0xFFD4D4D4), // Light gray text
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Quote block with vertical bar
 */
@Composable
fun QuoteView(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = FormattedTextColors.QuoteBackground,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Vertical bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(IntrinsicSize.Min)
                .fillMaxHeight()
                .background(
                    color = FormattedTextColors.QuoteBarColor,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        // Quote text
        Text(
            text = text,
            color = textColor.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Spoiler with beautiful reveal animation
 * Better than Telegram - with gradient reveal effect and smooth animation
 */
@Composable
fun SpoilerView(
    text: String,
    isRevealed: Boolean,
    textColor: Color,
    fontSize: TextUnit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blurAmount by animateFloatAsState(
        targetValue = if (isRevealed) 0f else 15f,
        animationSpec = tween(durationMillis = 300),
        label = "spoiler_blur"
    )

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 0.05f else 0.9f,
        animationSpec = tween(durationMillis = 300),
        label = "spoiler_bg_alpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                brush = if (!isRevealed) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF6B5B95),
                            Color(0xFF88B04B),
                            Color(0xFFFF6F61),
                            Color(0xFF6B5B95)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent
                        )
                    )
                }
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Hidden state - show placeholder
        AnimatedVisibility(
            visible = !isRevealed,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Spoiler",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Спойлер • Натисніть щоб показати",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Revealed state - show text with fade-in
        AnimatedVisibility(
            visible = isRevealed,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = Color(0x15000000),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Revealed",
                    tint = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = text,
                    color = textColor,
                    fontSize = fontSize
                )
            }
        }
    }
}

// ==================== PREVIEW HELPERS ====================

/**
 * Simple text preview without formatting (for chat list)
 */
@Composable
fun FormattedTextPreview(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = LocalContentColor.current,
    maxLines: Int = 1
) {
    val preview = remember(text) {
        TextFormattingParser.getPreviewText(text, 100)
    }

    Text(
        text = preview,
        style = style,
        color = color,
        maxLines = maxLines,
        modifier = modifier
    )
}
