package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.fonts.AppFonts

/**
 * Premium typography — a single font family (Exo 2) across the whole
 * premium channels UI to kill the previous 4-font cacophony.
 *
 * Exo 2 ships with Normal + Bold locally; visual hierarchy beyond two
 * weights is produced via size, tracking and line height. Tabular
 * numerals are turned on for metric rows so counts don't jitter.
 */
data class PremiumTypography(
    val display: TextStyle = BaseDisplay,
    val title: TextStyle = BaseTitle,
    val titleSmall: TextStyle = BaseTitleSmall,
    val body: TextStyle = BaseBody,
    val bodyStrong: TextStyle = BaseBodyStrong,
    val caption: TextStyle = BaseCaption,
    val overline: TextStyle = BaseOverline,
    val metric: TextStyle = BaseMetric,
    val button: TextStyle = BaseButton,
) {
    companion object {
        private val Family = AppFonts.Exo2

        val BaseDisplay = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.5).sp,
        )

        val BaseTitle = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
        )

        val BaseTitleSmall = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        )

        val BaseBody = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp,
        )

        val BaseBodyStrong = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp,
        )

        val BaseCaption = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        )

        val BaseOverline = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Start,
        )

        val BaseMetric = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp,
            fontFeatureSettings = "tnum",
        )

        val BaseButton = TextStyle(
            fontFamily = Family,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.6.sp,
        )

        val Default = PremiumTypography()
    }
}
