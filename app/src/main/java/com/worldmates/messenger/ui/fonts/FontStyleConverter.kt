package com.worldmates.messenger.ui.fonts

/**
 * Converts text to Unicode decorative variants.
 *
 * Latin-block styles  : remap A–Z / a–z / 0–9 to Mathematical Alphanumeric Symbols.
 * Combining-mark styles: append a combining Unicode mark after every non-whitespace
 *   code point — works for ALL scripts including Cyrillic/Ukrainian/Russian.
 * Enclosed-Latin styles: remap A–Z to Enclosed Alphanumeric Supplement (supplementary plane).
 * Combined styles      : apply a Latin-block conversion, then append a combining mark.
 */
object FontStyleConverter {

    fun convert(text: String, style: FontStyle): String = when (style) {
        // ── existing Latin-block styles ──────────────────────────────────────
        FontStyle.NORMAL                 -> text
        FontStyle.BOLD                   -> toBold(text)
        FontStyle.ITALIC                 -> toItalic(text)
        FontStyle.BOLD_ITALIC            -> toBoldItalic(text)
        FontStyle.SCRIPT                 -> toScript(text)
        FontStyle.BOLD_SCRIPT            -> toBoldScript(text)
        FontStyle.FRAKTUR                -> toFraktur(text)
        FontStyle.BOLD_FRAKTUR           -> toBoldFraktur(text)
        FontStyle.DOUBLE_STRUCK          -> toDoubleStruck(text)
        FontStyle.MONOSPACE              -> toMonospace(text)
        FontStyle.SANS_SERIF             -> toSansSerif(text)
        FontStyle.SANS_SERIF_BOLD        -> toSansSerifBold(text)
        FontStyle.SANS_SERIF_ITALIC      -> toSansSerifItalic(text)
        FontStyle.SANS_SERIF_BOLD_ITALIC -> toSansSerifBoldItalic(text)
        FontStyle.FULLWIDTH              -> toFullwidth(text)
        FontStyle.SMALL_CAPS             -> toSmallCaps(text)
        FontStyle.CIRCLED                -> toCircled(text)
        // ── combining marks (ALL scripts) ────────────────────────────────────
        FontStyle.STRIKETHROUGH          -> addCombiningMark(text, '\u0336')
        FontStyle.UNDERLINE              -> addCombiningMark(text, '\u0332')
        FontStyle.DOUBLE_UNDERLINE       -> addCombiningMark(text, '\u0333')
        FontStyle.OVERLINE               -> addCombiningMark(text, '\u0305')
        FontStyle.DOUBLE_OVERLINE        -> addCombiningMark(text, '\u033F')
        FontStyle.WAVY                   -> addCombiningMark(text, '\u0330')
        FontStyle.SLASH                  -> addCombiningMark(text, '\u0338')
        FontStyle.TILDE_OVERLAY          -> addCombiningMark(text, '\u0334')
        FontStyle.DOTTED                 -> addCombiningMark(text, '\u0307')
        FontStyle.DIAERESIS              -> addCombiningMark(text, '\u0308')
        FontStyle.CIRCLE_OVERLAY         -> addCombiningMark(text, '\u20DD')
        FontStyle.SQUARE_OVERLAY         -> addCombiningMark(text, '\u20DE')
        FontStyle.DIAMOND_OVERLAY        -> addCombiningMark(text, '\u20DF')
        // ── enclosed Latin ───────────────────────────────────────────────────
        FontStyle.NEGATIVE_CIRCLED       -> toEnclosedLatin(text, 0x1F150)
        FontStyle.PARENTHESIZED_CAPS     -> toEnclosedLatin(text, 0x1F110)
        FontStyle.SQUARED_LATIN          -> toEnclosedLatin(text, 0x1F130)
        FontStyle.PARENTHESIZED          -> toParenthesized(text)
        // ── combined: Latin block + underline ────────────────────────────────
        FontStyle.BOLD_UNDERLINE         -> addCombiningMark(toBold(text),          '\u0332')
        FontStyle.ITALIC_UNDERLINE       -> addCombiningMark(toItalic(text),        '\u0332')
        FontStyle.SCRIPT_UNDERLINE       -> addCombiningMark(toScript(text),        '\u0332')
        FontStyle.BOLD_SCRIPT_UNDERLINE  -> addCombiningMark(toBoldScript(text),    '\u0332')
        FontStyle.MONOSPACE_UNDERLINE    -> addCombiningMark(toMonospace(text),     '\u0332')
        FontStyle.SANS_BOLD_UNDERLINE    -> addCombiningMark(toSansSerifBold(text), '\u0332')
        // ── combined: Latin block + strikethrough ────────────────────────────
        FontStyle.BOLD_STRIKETHROUGH     -> addCombiningMark(toBold(text),          '\u0336')
        FontStyle.ITALIC_STRIKETHROUGH   -> addCombiningMark(toItalic(text),        '\u0336')
        FontStyle.SCRIPT_STRIKETHROUGH   -> addCombiningMark(toScript(text),        '\u0336')
        FontStyle.MONOSPACE_STRIKETHROUGH-> addCombiningMark(toMonospace(text),     '\u0336')
        // ── combined: multi-mark ─────────────────────────────────────────────
        FontStyle.OVERLINE_UNDERLINE     -> addCombiningMark(text,            '\u0305', '\u0332')
        FontStyle.WAVY_STRIKETHROUGH     -> addCombiningMark(text,            '\u0334', '\u0336')
        FontStyle.BOLD_FRAKTUR_UNDERLINE -> addCombiningMark(toBoldFraktur(text),   '\u0332')
        FontStyle.DOUBLE_STRUCK_UNDERLINE-> addCombiningMark(toDoubleStruck(text),  '\u0332')
        FontStyle.DIAERESIS_OVERLINE     -> addCombiningMark(text,            '\u0308', '\u0305')
    }

    // ═══════════════════════ CORE HELPERS ════════════════════════════════════

    /**
     * Appends one or more combining Unicode marks after every non-whitespace code point.
     * Iterates by code point (not char) so surrogate pairs are handled correctly.
     */
    private fun addCombiningMark(text: String, vararg marks: Char): String = buildString {
        var i = 0
        while (i < text.length) {
            val cp    = text.codePointAt(i)
            val count = Character.charCount(cp)
            for (j in 0 until count) append(text[i + j])
            if (!Character.isWhitespace(cp)) marks.forEach { append(it) }
            i += count
        }
    }

    private fun codePointToString(cp: Int) = String(Character.toChars(cp))

    /** Remaps ASCII letters/digits to a Unicode Mathematical block. */
    private fun mathAlpha(
        text: String,
        upperBase: Int,
        lowerBase: Int,
        digitBase: Int = -1,
        exceptions: Map<Char, String> = emptyMap()
    ): String = buildString {
        for (ch in text) {
            val mapped = exceptions[ch]
            when {
                mapped != null      -> append(mapped)
                ch in 'A'..'Z'     -> append(codePointToString(upperBase + (ch - 'A')))
                ch in 'a'..'z'     -> append(codePointToString(lowerBase + (ch - 'a')))
                digitBase >= 0 && ch in '0'..'9' -> append(codePointToString(digitBase + (ch - '0')))
                else               -> append(ch)
            }
        }
    }

    // ═══════════════════════ LATIN MATH BLOCK STYLES ═════════════════════════

    private fun toBold(text: String) = mathAlpha(text,
        upperBase = 0x1D400, lowerBase = 0x1D41A, digitBase = 0x1D7CE)

    private fun toItalic(text: String) = mathAlpha(text,
        upperBase = 0x1D434, lowerBase = 0x1D44E,
        exceptions = mapOf('h' to "\u210E"))

    private fun toBoldItalic(text: String) = mathAlpha(text,
        upperBase = 0x1D468, lowerBase = 0x1D482)

    private fun toScript(text: String) = mathAlpha(text,
        upperBase = 0x1D49C, lowerBase = 0x1D4B6,
        exceptions = mapOf(
            'B' to "\u212C", 'E' to "\u2130", 'F' to "\u2131",
            'H' to "\u210B", 'I' to "\u2110", 'L' to "\u2112",
            'M' to "\u2133", 'R' to "\u211B",
            'e' to "\u212F", 'g' to "\u210A", 'o' to "\u2134"))

    private fun toBoldScript(text: String) = mathAlpha(text,
        upperBase = 0x1D4D0, lowerBase = 0x1D4EA)

    private fun toFraktur(text: String) = mathAlpha(text,
        upperBase = 0x1D504, lowerBase = 0x1D51E,
        exceptions = mapOf(
            'C' to "\u212D", 'H' to "\u210C", 'I' to "\u2111",
            'R' to "\u211C", 'Z' to "\u2128"))

    private fun toBoldFraktur(text: String) = mathAlpha(text,
        upperBase = 0x1D56C, lowerBase = 0x1D586)

    private fun toDoubleStruck(text: String) = mathAlpha(text,
        upperBase = 0x1D538, lowerBase = 0x1D552, digitBase = 0x1D7D8,
        exceptions = mapOf(
            'C' to "\u2102", 'H' to "\u210D", 'N' to "\u2115",
            'P' to "\u2119", 'Q' to "\u211A", 'R' to "\u211D", 'Z' to "\u2124"))

    private fun toMonospace(text: String) = mathAlpha(text,
        upperBase = 0x1D670, lowerBase = 0x1D68A, digitBase = 0x1D7F6)

    private fun toSansSerif(text: String) = mathAlpha(text,
        upperBase = 0x1D5A0, lowerBase = 0x1D5BA, digitBase = 0x1D7E2)

    private fun toSansSerifBold(text: String) = mathAlpha(text,
        upperBase = 0x1D5D4, lowerBase = 0x1D5EE, digitBase = 0x1D7EC)

    private fun toSansSerifItalic(text: String) = mathAlpha(text,
        upperBase = 0x1D608, lowerBase = 0x1D622)

    private fun toSansSerifBoldItalic(text: String) = mathAlpha(text,
        upperBase = 0x1D63C, lowerBase = 0x1D656)

    private fun toFullwidth(text: String) = buildString {
        for (ch in text) {
            append(when (ch) {
                in 'A'..'Z' -> (0xFF21 + (ch - 'A')).toChar()
                in 'a'..'z' -> (0xFF41 + (ch - 'a')).toChar()
                in '0'..'9' -> (0xFF10 + (ch - '0')).toChar()
                ' '         -> '\u3000'
                else        -> ch
            })
        }
    }

    private val smallCapsMap = mapOf(
        'a' to 'ᴀ', 'b' to 'ʙ', 'c' to 'ᴄ', 'd' to 'ᴅ', 'e' to 'ᴇ',
        'f' to 'ꜰ', 'g' to 'ɢ', 'h' to 'ʜ', 'i' to 'ɪ', 'j' to 'ᴊ',
        'k' to 'ᴋ', 'l' to 'ʟ', 'm' to 'ᴍ', 'n' to 'ɴ', 'o' to 'ᴏ',
        'p' to 'ᴘ', 'q' to 'ǫ', 'r' to 'ʀ', 's' to 'ꜱ', 't' to 'ᴛ',
        'u' to 'ᴜ', 'v' to 'ᴠ', 'w' to 'ᴡ', 'x' to 'x', 'y' to 'ʏ', 'z' to 'ᴢ')

    private fun toSmallCaps(text: String) = buildString {
        for (ch in text) {
            append(when {
                ch in 'a'..'z' -> smallCapsMap[ch] ?: ch
                ch in 'A'..'Z' -> smallCapsMap[ch.lowercaseChar()] ?: ch
                else           -> ch
            })
        }
    }

    private fun toCircled(text: String) = buildString {
        for (ch in text) {
            append(when (ch) {
                in 'A'..'Z' -> (0x24B6 + (ch - 'A')).toChar()
                in 'a'..'z' -> (0x24D0 + (ch - 'a')).toChar()
                else        -> ch
            })
        }
    }

    // ═══════════════════════ ENCLOSED LATIN ══════════════════════════════════

    /**
     * Maps A–Z (and a–z → uppercase) to a supplementary-plane block starting at [base].
     * Used for NEGATIVE_CIRCLED (U+1F150), PARENTHESIZED_CAPS (U+1F110), SQUARED_LATIN (U+1F130).
     */
    private fun toEnclosedLatin(text: String, base: Int): String = buildString {
        for (ch in text) {
            when (ch) {
                in 'A'..'Z' -> append(codePointToString(base + (ch - 'A')))
                in 'a'..'z' -> append(codePointToString(base + (ch.uppercaseChar() - 'A')))
                else        -> append(ch)
            }
        }
    }

    /** Maps a–z (and A–Z → lowercase) to Enclosed Alphanumeric ⒜–⒵ (U+249C). */
    private fun toParenthesized(text: String): String = buildString {
        for (ch in text) {
            append(when (ch) {
                in 'a'..'z' -> (0x249C + (ch - 'a')).toChar()
                in 'A'..'Z' -> (0x249C + (ch.lowercaseChar() - 'a')).toChar()
                else        -> ch
            })
        }
    }
}
