package com.worldmates.messenger.ui.components.formatting

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Text Formatting System for WorldMates Messenger
 *
 * Supports:
 * - @mentions (clickable usernames)
 * - #hashtags (clickable tags)
 * - Markdown formatting:
 *   - **bold** or __bold__
 *   - *italic* or _italic_
 *   - `code` (monospace)
 *   - ```multiline code```
 *   - ~~strikethrough~~
 *   - __underline__ (double underscore at start/end)
 *   - ||spoiler|| (hidden text, tap to reveal)
 *   - > quote (blockquote)
 *   - [link text](url)
 */

// ==================== DATA MODELS ====================

/**
 * Represents a parsed text segment with formatting
 */
sealed class FormattedSegment {
    data class Plain(val text: String) : FormattedSegment()
    data class Bold(val text: String) : FormattedSegment()
    data class Italic(val text: String) : FormattedSegment()
    data class BoldItalic(val text: String) : FormattedSegment()
    data class Code(val text: String) : FormattedSegment()
    data class CodeBlock(val text: String, val language: String? = null) : FormattedSegment()
    data class Strikethrough(val text: String) : FormattedSegment()
    data class Underline(val text: String) : FormattedSegment()
    data class Spoiler(val text: String) : FormattedSegment()
    data class Quote(val text: String) : FormattedSegment()
    data class Mention(val username: String, val userId: Long? = null) : FormattedSegment()
    data class Hashtag(val tag: String) : FormattedSegment()
    data class Link(val text: String, val url: String) : FormattedSegment()
    data class AutoLink(val url: String) : FormattedSegment()
}

/**
 * Result of parsing formatted text
 */
data class ParsedFormattedText(
    val segments: List<FormattedSegment>,
    val mentions: List<String>,
    val hashtags: List<String>,
    val links: List<String>,
    val hasSpoilers: Boolean
)

/**
 * Formatting settings for a chat/channel
 */
data class FormattingSettings(
    val allowMentions: Boolean = true,
    val allowHashtags: Boolean = true,
    val allowBold: Boolean = true,
    val allowItalic: Boolean = true,
    val allowCode: Boolean = true,
    val allowStrikethrough: Boolean = true,
    val allowUnderline: Boolean = true,
    val allowSpoilers: Boolean = true,
    val allowQuotes: Boolean = true,
    val allowLinks: Boolean = true
)

// ==================== PARSER ====================

object TextFormattingParser {

    private const val TAG = "TextFormattingParser"

    // Regex patterns
    private val MENTION_PATTERN = Regex("""@(\w+)""")
    private val HASHTAG_PATTERN = Regex("""#([\w\u0400-\u04FF]+)""") // Supports Cyrillic
    private val BOLD_PATTERN = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val ITALIC_PATTERN = Regex("""\*(.+?)\*|_(.+?)_""")
    private val BOLD_ITALIC_PATTERN = Regex("""\*\*\*(.+?)\*\*\*|___(.+?)___""")
    private val CODE_PATTERN = Regex("""`([^`]+)`""")
    private val CODE_BLOCK_PATTERN = Regex("""```(\w*)\n?([\s\S]*?)```""")
    private val STRIKETHROUGH_PATTERN = Regex("""~~(.+?)~~""")
    private val UNDERLINE_PATTERN = Regex("""<u>(.+?)</u>""")
    private val SPOILER_PATTERN = Regex("""\|\|(.+?)\|\|""")
    private val QUOTE_PATTERN = Regex("""^>\s*(.+)$""", RegexOption.MULTILINE)
    private val LINK_PATTERN = Regex("""\[(.+?)]\((.+?)\)""")
    private val AUTO_LINK_PATTERN = Regex("""https?://[^\s<>\[\]]+""")

    /**
     * Parse text and extract all formatted segments
     */
    fun parse(text: String, settings: FormattingSettings = FormattingSettings()): ParsedFormattedText {
        if (text.isBlank()) {
            return ParsedFormattedText(
                segments = listOf(FormattedSegment.Plain("")),
                mentions = emptyList(),
                hashtags = emptyList(),
                links = emptyList(),
                hasSpoilers = false
            )
        }

        val mentions = mutableListOf<String>()
        val hashtags = mutableListOf<String>()
        val links = mutableListOf<String>()
        var hasSpoilers = false

        // Build list of all matches with their positions
        data class Match(val start: Int, val end: Int, val segment: FormattedSegment)
        val allMatches = mutableListOf<Match>()

        // Find code blocks first (highest priority, prevents parsing inside)
        if (settings.allowCode) {
            CODE_BLOCK_PATTERN.findAll(text).forEach { match ->
                val language = match.groupValues[1].ifEmpty { null }
                val code = match.groupValues[2]
                allMatches.add(Match(match.range.first, match.range.last + 1,
                    FormattedSegment.CodeBlock(code, language)))
            }
        }

        // Find inline code
        if (settings.allowCode) {
            CODE_PATTERN.findAll(text).forEach { match ->
                // Skip if inside code block
                if (allMatches.none { it.start <= match.range.first && it.end >= match.range.last + 1 }) {
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Code(match.groupValues[1])))
                }
            }
        }

        // Helper to check if position is inside existing match
        fun isInsideExisting(start: Int, end: Int): Boolean {
            return allMatches.any { existing ->
                (start >= existing.start && start < existing.end) ||
                (end > existing.start && end <= existing.end)
            }
        }

        // Find spoilers
        if (settings.allowSpoilers) {
            SPOILER_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    hasSpoilers = true
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Spoiler(match.groupValues[1])))
                }
            }
        }

        // Find bold+italic (***text***)
        if (settings.allowBold && settings.allowItalic) {
            BOLD_ITALIC_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.BoldItalic(content)))
                }
            }
        }

        // Find bold (**text** or __text__)
        if (settings.allowBold) {
            BOLD_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Bold(content)))
                }
            }
        }

        // Find italic (*text* or _text_)
        if (settings.allowItalic) {
            ITALIC_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Italic(content)))
                }
            }
        }

        // Find strikethrough
        if (settings.allowStrikethrough) {
            STRIKETHROUGH_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Strikethrough(match.groupValues[1])))
                }
            }
        }

        // Find underline (<u>text</u>)
        if (settings.allowUnderline) {
            UNDERLINE_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Underline(match.groupValues[1])))
                }
            }
        }

        // Find quotes
        if (settings.allowQuotes) {
            QUOTE_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Quote(match.groupValues[1])))
                }
            }
        }

        // Find links [text](url)
        if (settings.allowLinks) {
            LINK_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    val linkText = match.groupValues[1]
                    val url = match.groupValues[2]
                    links.add(url)
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Link(linkText, url)))
                }
            }
        }

        // Find auto-links (URLs)
        if (settings.allowLinks) {
            AUTO_LINK_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    links.add(match.value)
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.AutoLink(match.value)))
                }
            }
        }

        // Find mentions
        if (settings.allowMentions) {
            MENTION_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    val username = match.groupValues[1]
                    mentions.add(username)
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Mention(username)))
                }
            }
        }

        // Find hashtags
        if (settings.allowHashtags) {
            HASHTAG_PATTERN.findAll(text).forEach { match ->
                if (!isInsideExisting(match.range.first, match.range.last + 1)) {
                    val tag = match.groupValues[1]
                    hashtags.add(tag)
                    allMatches.add(Match(match.range.first, match.range.last + 1,
                        FormattedSegment.Hashtag(tag)))
                }
            }
        }

        // Sort matches by position
        val sortedMatches = allMatches.sortedBy { it.start }

        // Build final segments list
        val segments = mutableListOf<FormattedSegment>()
        var currentPos = 0

        for (match in sortedMatches) {
            // Add plain text before this match
            if (match.start > currentPos) {
                val plainText = text.substring(currentPos, match.start)
                if (plainText.isNotEmpty()) {
                    segments.add(FormattedSegment.Plain(plainText))
                }
            }

            // Add the formatted segment
            if (match.start >= currentPos) {
                segments.add(match.segment)
                currentPos = match.end
            }
        }

        // Add remaining plain text
        if (currentPos < text.length) {
            segments.add(FormattedSegment.Plain(text.substring(currentPos)))
        }

        // If no segments, add the whole text as plain
        if (segments.isEmpty()) {
            segments.add(FormattedSegment.Plain(text))
        }

        return ParsedFormattedText(
            segments = segments,
            mentions = mentions.distinct(),
            hashtags = hashtags.distinct(),
            links = links.distinct(),
            hasSpoilers = hasSpoilers
        )
    }

    /**
     * Convert formatted text back to raw text (strip formatting)
     */
    fun stripFormatting(text: String): String {
        var result = text

        // Remove code blocks
        result = CODE_BLOCK_PATTERN.replace(result) { it.groupValues[2] }

        // Remove inline code
        result = CODE_PATTERN.replace(result) { it.groupValues[1] }

        // Remove spoilers
        result = SPOILER_PATTERN.replace(result) { it.groupValues[1] }

        // Remove bold+italic
        result = BOLD_ITALIC_PATTERN.replace(result) { it.groupValues[1].ifEmpty { it.groupValues[2] } }

        // Remove bold
        result = BOLD_PATTERN.replace(result) { it.groupValues[1].ifEmpty { it.groupValues[2] } }

        // Remove italic
        result = ITALIC_PATTERN.replace(result) { it.groupValues[1].ifEmpty { it.groupValues[2] } }

        // Remove strikethrough
        result = STRIKETHROUGH_PATTERN.replace(result) { it.groupValues[1] }

        // Remove underline
        result = UNDERLINE_PATTERN.replace(result) { it.groupValues[1] }

        // Remove quote markers
        result = QUOTE_PATTERN.replace(result) { it.groupValues[1] }

        // Replace links with just text
        result = LINK_PATTERN.replace(result) { it.groupValues[1] }

        return result
    }

    /**
     * Get preview text (for chat list, notifications)
     */
    fun getPreviewText(text: String, maxLength: Int = 100): String {
        val stripped = stripFormatting(text)
        return if (stripped.length > maxLength) {
            stripped.take(maxLength - 3) + "..."
        } else {
            stripped
        }
    }
}

// ==================== FORMATTING HELPERS ====================

/**
 * Apply formatting to selected text
 */
object TextFormattingHelper {

    fun applyBold(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
        return wrapSelection(text, selectionStart, selectionEnd, "**", "**")
    }

    fun applyItalic(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
        return wrapSelection(text, selectionStart, selectionEnd, "*", "*")
    }

    fun applyCode(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
        return wrapSelection(text, selectionStart, selectionEnd, "`", "`")
    }

    fun applyStrikethrough(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
        return wrapSelection(text, selectionStart, selectionEnd, "~~", "~~")
    }

    fun applyUnderline(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
        return wrapSelection(text, selectionStart, selectionEnd, "<u>", "</u>")
    }

    fun applySpoiler(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
        return wrapSelection(text, selectionStart, selectionEnd, "||", "||")
    }

    fun applyQuote(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
        val selectedText = text.substring(selectionStart, selectionEnd)
        val quotedText = selectedText.lines().joinToString("\n") { "> $it" }
        val newText = text.substring(0, selectionStart) + quotedText + text.substring(selectionEnd)
        return Pair(newText, selectionStart + quotedText.length)
    }

    fun applyLink(text: String, selectionStart: Int, selectionEnd: Int, url: String): Pair<String, Int> {
        val selectedText = text.substring(selectionStart, selectionEnd)
        val linkText = "[$selectedText]($url)"
        val newText = text.substring(0, selectionStart) + linkText + text.substring(selectionEnd)
        return Pair(newText, selectionStart + linkText.length)
    }

    fun insertMention(text: String, cursorPos: Int, username: String): Pair<String, Int> {
        val mention = "@$username "
        val newText = text.substring(0, cursorPos) + mention + text.substring(cursorPos)
        return Pair(newText, cursorPos + mention.length)
    }

    fun insertHashtag(text: String, cursorPos: Int, tag: String): Pair<String, Int> {
        val hashtag = "#$tag "
        val newText = text.substring(0, cursorPos) + hashtag + text.substring(cursorPos)
        return Pair(newText, cursorPos + hashtag.length)
    }

    private fun wrapSelection(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        prefix: String,
        suffix: String
    ): Pair<String, Int> {
        val selectedText = text.substring(selectionStart, selectionEnd)
        val wrappedText = "$prefix$selectedText$suffix"
        val newText = text.substring(0, selectionStart) + wrappedText + text.substring(selectionEnd)
        return Pair(newText, selectionStart + wrappedText.length)
    }
}
