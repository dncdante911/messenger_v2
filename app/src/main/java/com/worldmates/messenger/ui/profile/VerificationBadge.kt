package com.worldmates.messenger.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
