package com.worldmates.messenger.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.LinkPreviewData
import com.worldmates.messenger.network.NodeRetrofitClient

// ─── URL regex ────────────────────────────────────────────────────────────────

private val URL_REGEX = Regex(
    """https?://[^\s<>"']+""",
    RegexOption.IGNORE_CASE
)

// ─── Inline formatting tokens ─────────────────────────────────────────────────

private sealed interface Token {
    data class Bold(val text: String) : Token
    data class Italic(val text: String) : Token
    data class Code(val text: String) : Token
    data class Link(val label: String, val url: String) : Token
    data class Plain(val text: String) : Token
}

/**
 * Very lightweight Markdown-subset tokenizer.
 * Supported patterns (processed in order):
 *   `code`           — inline code
 *   **bold**         — bold text
 *   *italic*         — italic text
 *   [label](url)     — hyperlink
 *   bare URL         — auto-linked
 */
private fun tokenize(raw: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var remaining = raw

    val combined = Regex(
        """`([^`]+)`"""                             +   // code
        """|(\*\*(.+?)\*\*)"""                      +   // **bold**
        """|\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""         +   // *italic*
        """|\[([^\]]+)\]\((https?://[^)]+)\)"""     +   // [label](url)
        """|(https?://\S+)""",                          // bare URL
        RegexOption.DOT_MATCHES_ALL
    )

    var lastEnd = 0
    for (match in combined.findAll(remaining)) {
        if (match.range.first > lastEnd) {
            tokens += Token.Plain(remaining.substring(lastEnd, match.range.first))
        }
        val g = match.groupValues
        tokens += when {
            g[1].isNotEmpty()  -> Token.Code(g[1])
            g[3].isNotEmpty()  -> Token.Bold(g[3])
            g[4].isNotEmpty()  -> Token.Italic(g[4])
            g[5].isNotEmpty() && g[6].isNotEmpty() -> Token.Link(g[5], g[6])
            g[7].isNotEmpty()  -> Token.Link(g[7], g[7])
            else               -> Token.Plain(match.value)
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < remaining.length) {
        tokens += Token.Plain(remaining.substring(lastEnd))
    }
    return tokens
}

/**
 * Build a [AnnotatedString] from tokens, wiring up clickable URL spans.
 */
@Composable
private fun buildBioAnnotated(
    tokens: List<Token>,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    val codeStyle = SpanStyle(
        fontFamily      = FontFamily.Monospace,
        background      = MaterialTheme.colorScheme.surfaceVariant,
        color           = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    for (token in tokens) {
        when (token) {
            is Token.Plain  -> append(token.text)
            is Token.Bold   -> withStyle(SpanStyle(fontWeight = FontWeight.Bold))   { append(token.text) }
            is Token.Italic -> withStyle(SpanStyle(fontStyle  = FontStyle.Italic))  { append(token.text) }
            is Token.Code   -> withStyle(codeStyle)                                  { append(token.text) }
            is Token.Link   -> {
                pushStringAnnotation("URL", token.url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(token.label)
                }
                pop()
            }
        }
    }
}

/**
 * Renders a bio string with lightweight Markdown-subset formatting:
 * **bold**, *italic*, `code`, [label](url), bare URLs.
 *
 * Tapping a URL opens it in the system browser.
 * The first bare URL (or Markdown link) found in the bio is fetched for an OG
 * preview card rendered below the text.
 *
 * @param text          raw bio text
 * @param showPreview   fetch and render link preview card for the first URL
 */
@Composable
fun FormattedBioText(
    text: String,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
) {
    val uriHandler  = LocalUriHandler.current
    val linkColor   = MaterialTheme.colorScheme.primary
    val tokens      = remember(text) { tokenize(text) }
    val annotated   = buildBioAnnotated(tokens, linkColor)

    // Find the first URL in the text for link preview
    val firstUrl: String? = remember(tokens) {
        tokens.filterIsInstance<Token.Link>().firstOrNull()?.url
            ?: URL_REGEX.find(text)?.value
    }

    var previewData    by remember { mutableStateOf<LinkPreviewData?>(null) }
    var previewLoading by remember { mutableStateOf(false) }

    // Fetch OG metadata once per URL — LaunchedEffect must NOT be inside an if()
    LaunchedEffect(firstUrl, showPreview) {
        if (!showPreview || firstUrl == null) return@LaunchedEffect
        previewLoading = true
        previewData    = null
        runCatching {
            NodeRetrofitClient.profileApi.getLinkPreview(firstUrl)
        }.onSuccess { resp ->
            previewData = resp.toData()
        }
        previewLoading = false
    }

    Column(modifier = modifier) {
        // ── Formatted text ────────────────────────────────────────────────────
        ClickableText(
            text  = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()?.let { annotation ->
                        runCatching { uriHandler.openUri(annotation.item) }
                    }
            },
        )

        // ── Link preview card ─────────────────────────────────────────────────
        if (showPreview) {
            when {
                previewLoading -> {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(
                        modifier  = Modifier.height(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
                previewData != null -> {
                    Spacer(Modifier.height(8.dp))
                    LinkPreviewCard(data = previewData!!)
                }
            }
        }
    }
}
