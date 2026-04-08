package com.worldmates.messenger.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.worldmates.messenger.R

/**
 * Verification level badge displayed next to a user's name.
 *
 * Levels:
 *   0 — none (nothing rendered)
 *   1 — verified:    blue  ✓  (CheckCircle)
 *   2 — notable:     gold  ★  (Star)
 *   3 — official:    green ✓  (CheckCircle)
 *   4 — top-creator: purple ♦  (diamond text glyph inside tinted circle)
 */
@Composable
fun VerificationBadge(
    level: Int,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    when (level) {
        1 -> Icon(
            imageVector        = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.verification_level_1),
            tint               = Color(0xFF1A8CFF),   // blue
            modifier           = modifier.size(size),
        )
        2 -> Icon(
            imageVector        = Icons.Default.Star,
            contentDescription = stringResource(R.string.verification_level_2),
            tint               = Color(0xFFFFC107),   // gold
            modifier           = modifier.size(size),
        )
        3 -> Icon(
            imageVector        = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.verification_level_3),
            tint               = Color(0xFF2ECC71),   // green
            modifier           = modifier.size(size),
        )
        4 -> Box(
            modifier          = modifier
                .size(size)
                .background(Color(0xFF9B59B6), CircleShape),
            contentAlignment  = Alignment.Center,
        ) {
            // Unicode diamond ◆ as fallback (no Material icon for diamond in M3)
            Text(
                text     = "◆",
                color    = Color.White,
                fontSize = (size.value * 0.55f).sp,
            )
        }
        // level == 0 or any unknown value → render nothing
    }
}

/**
 * Founding-member badge — shown for the first 250 users registered after launch.
 *
 * Design: small teal circle with a ✦ (4-pointed star) glyph inside.
 * Intentionally a touch smaller than the verification badge (defaults to 16 dp)
 * to be visible but not dominating.
 *
 * Usage: place it next to [VerificationBadge] or standalone after a username.
 *
 * @param isFounder pass [User.isFounder]; renders nothing if 0.
 */
@Composable
fun FoundingMemberBadge(
    isFounder: Int,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    if (isFounder != 1) return

    Box(
        modifier         = modifier
            .size(size)
            .background(Color(0xFF00897B), CircleShape),   // teal-700 — distinct from all other badges
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text             = "✦",                       // 4-pointed star (U+2726)
            color            = Color.White,
            fontSize         = (size.value * 0.58f).sp,
        )
    }
}

/**
 * Convenience composable: renders [VerificationBadge] and [FoundingMemberBadge]
 * side-by-side (verification first, then founder), with a 2 dp gap.
 * Use this wherever you want both badges without duplicating layout logic.
 */
@Composable
fun UserBadgeRow(
    verificationLevel: Int,
    isFounder: Int,
    verificationSize: Dp = 20.dp,
    founderSize: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val showVerification = verificationLevel > 0
    val showFounder      = isFounder == 1
    if (!showVerification && !showFounder) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = modifier,
    ) {
        if (showVerification) {
            VerificationBadge(level = verificationLevel, size = verificationSize)
        }
        if (showVerification && showFounder) {
            Spacer(Modifier.width(2.dp))
        }
        if (showFounder) {
            FoundingMemberBadge(isFounder = isFounder, size = founderSize)
        }
    }
}
