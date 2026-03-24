package com.worldmates.messenger.ui.fonts

/**
 * Converts text to Unicode decorative variants using combining marks.
 * All styles work for every script: Cyrillic, Ukrainian, Latin, digits, etc.
 * A combining mark is appended after every non-whitespace code point.
 */
object FontStyleConverter {

    fun convert(text: String, style: FontStyle): String = when (style) {
        FontStyle.NORMAL -> text

        // ── Single combining marks ────────────────────────────────────────────
        FontStyle.STRIKETHROUGH              -> addMark(text, '\u0336')
        FontStyle.UNDERLINE                  -> addMark(text, '\u0332')
        FontStyle.DOUBLE_UNDERLINE           -> addMark(text, '\u0333')
        FontStyle.OVERLINE                   -> addMark(text, '\u0305')
        FontStyle.DOUBLE_OVERLINE            -> addMark(text, '\u033F')
        FontStyle.WAVY                       -> addMark(text, '\u0330')
        FontStyle.SLASH                      -> addMark(text, '\u0338')
        FontStyle.TILDE_OVERLAY              -> addMark(text, '\u0334')
        FontStyle.DOTTED                     -> addMark(text, '\u0307')
        FontStyle.DIAERESIS                  -> addMark(text, '\u0308')
        FontStyle.RING_ABOVE                 -> addMark(text, '\u030A')
        FontStyle.DOT_BELOW                  -> addMark(text, '\u0323')
        FontStyle.RING_BELOW                 -> addMark(text, '\u0325')

        // ── Accent / diacritic marks ──────────────────────────────────────────
        FontStyle.GRAVE                      -> addMark(text, '\u0300')
        FontStyle.ACUTE                      -> addMark(text, '\u0301')
        FontStyle.CIRCUMFLEX                 -> addMark(text, '\u0302')
        FontStyle.MACRON                     -> addMark(text, '\u0304')
        FontStyle.BREVE                      -> addMark(text, '\u0306')
        FontStyle.CARON                      -> addMark(text, '\u030C')

        // ── Enclosing / special symbols ───────────────────────────────────────
        FontStyle.CIRCLE_OVERLAY             -> addMark(text, '\u20DD')
        FontStyle.SQUARE_OVERLAY             -> addMark(text, '\u20DE')
        FontStyle.DIAMOND_OVERLAY            -> addMark(text, '\u20DF')
        FontStyle.ARROW_ABOVE                -> addMark(text, '\u20D7')

        // ── Cyrillic-specific / extended ─────────────────────────────────────
        FontStyle.CYRILLIC_TITLO             -> addMark(text, '\u0483')
        FontStyle.TRIPLE_DOT_ABOVE           -> addMark(text, '\u20DB')
        FontStyle.FOUR_DOTS_ABOVE            -> addMark(text, '\u20DC')
        FontStyle.TRIPLE_UNDERDOT            -> addMark(text, '\u20E8')

        // ── Multi-mark combinations ───────────────────────────────────────────
        FontStyle.OVERLINE_UNDERLINE         -> addMark(text, '\u0305', '\u0332')
        FontStyle.WAVY_STRIKETHROUGH         -> addMark(text, '\u0334', '\u0336')
        FontStyle.DIAERESIS_OVERLINE         -> addMark(text, '\u0308', '\u0305')
        FontStyle.DOTTED_STRIKETHROUGH       -> addMark(text, '\u0307', '\u0336')
        FontStyle.DOUBLE_UNDERLINE_OVERLINE  -> addMark(text, '\u0333', '\u0305')
        FontStyle.DIAERESIS_STRIKETHROUGH    -> addMark(text, '\u0308', '\u0336')
        FontStyle.CIRCLE_UNDERLINE           -> addMark(text, '\u20DD', '\u0332')

        // ── Triple combinations ───────────────────────────────────────────────
        FontStyle.OVERLINE_STRIKETHROUGH_UNDERLINE -> addMark(text, '\u0305', '\u0336', '\u0332')
        FontStyle.DOTTED_UNDERLINE           -> addMark(text, '\u0307', '\u0332')
        FontStyle.GRAVE_UNDERLINE            -> addMark(text, '\u0300', '\u0332')
        FontStyle.DIAERESIS_UNDERLINE        -> addMark(text, '\u0308', '\u0332')
        FontStyle.MACRON_UNDERLINE           -> addMark(text, '\u0304', '\u0332')
        FontStyle.CARON_STRIKETHROUGH        -> addMark(text, '\u030C', '\u0336')
        FontStyle.ACUTE_OVERLINE             -> addMark(text, '\u0301', '\u0305')
    }

    /**
     * Appends one or more combining Unicode marks after every non-whitespace code point.
     * Iterates by code point (not Char) so surrogate pairs are handled correctly.
     * The function name is intentionally short since it's used everywhere in this file.
     */
    private fun addMark(text: String, vararg marks: Char): String = buildString {
        var i = 0
        while (i < text.length) {
            val cp    = text.codePointAt(i)
            val count = Character.charCount(cp)
            for (j in 0 until count) append(text[i + j])
            if (!Character.isWhitespace(cp)) marks.forEach { append(it) }
            i += count
        }
    }
}
