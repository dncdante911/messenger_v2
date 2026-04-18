package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Uppercase overline with a short gold accent bar on the left. Used on
 * the purchase screen between sections (Choose Plan / Payment method /
 * Features) and in the Appearance settings screen.
 */
@Composable
fun PremiumSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val design = PremiumDesign.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 18.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PremiumBrushes.matteGoldLinear()),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = design.typography.overline.copy(color = design.colors.onSecondary),
        )
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier, contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

/** Hairline divider in matte gold at low alpha. */
@Composable
fun PremiumDivider(
    modifier: Modifier = Modifier,
) {
    val stroke = PremiumDesign.current.colors.glassStroke
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(stroke),
    )
}

/** Subtle animated gold hairline. Used under hero sections. */
@Composable
fun PremiumGoldHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PremiumBrushes.goldHairline()),
    )
}
