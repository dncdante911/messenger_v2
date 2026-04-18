package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.BannerPreset
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Procedural overlay pattern painted on top of the hero / list-card
 * gradient. Used to give individual premium channels a distinct
 * "character" without reissuing bitmap assets. Patterns always use the
 * current accent color at low opacity so they blend into either the
 * Obsidian or Ivory scheme.
 *
 * Banner = "none" short-circuits to an empty Box so the call site can
 * mount this unconditionally.
 */
@Composable
fun PremiumBannerPattern(
    banner: BannerPreset,
    modifier: Modifier = Modifier,
    strength: Float = 0.18f,
) {
    if (banner.id == "none") return
    val accent = PremiumDesign.current.colors.accent
    val tint = accent.copy(alpha = strength.coerceIn(0f, 1f))

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when (banner.id) {
            "dots" -> drawDots(tint, w, h)
            "diagonal" -> drawDiagonalWeave(tint, w, h)
            "diamond" -> drawDiamondGrid(tint, w, h)
            "hex" -> drawHexMesh(tint, w, h)
            "aurora_veil" -> drawAuroraVeil()
            else -> Unit
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDots(
    tint: Color, w: Float, h: Float,
) {
    val step = 18.dp.toPx()
    val r = 1.6.dp.toPx()
    var y = step / 2f
    while (y < h) {
        var x = step / 2f
        while (x < w) {
            drawCircle(color = tint, radius = r, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiagonalWeave(
    tint: Color, w: Float, h: Float,
) {
    val step = 22.dp.toPx()
    val strokeW = 1.dp.toPx()
    // Two crossing diagonals give the "woven" feel.
    var d = -h
    while (d < w + h) {
        drawLine(
            color = tint,
            start = Offset(d, 0f),
            end = Offset(d + h, h),
            strokeWidth = strokeW,
        )
        drawLine(
            color = tint,
            start = Offset(d, h),
            end = Offset(d + h, 0f),
            strokeWidth = strokeW,
        )
        d += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiamondGrid(
    tint: Color, w: Float, h: Float,
) {
    val step = 24.dp.toPx()
    val strokeW = 1.dp.toPx()
    val half = step / 2f
    var y = 0f
    while (y < h + step) {
        var x = 0f
        while (x < w + step) {
            val path = Path().apply {
                moveTo(x, y)
                lineTo(x + half, y + half)
                lineTo(x, y + step)
                lineTo(x - half, y + half)
                close()
            }
            drawPath(path, color = tint, style = Stroke(width = strokeW))
            x += step
        }
        y += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexMesh(
    tint: Color, w: Float, h: Float,
) {
    val r = 14.dp.toPx()
    val strokeW = 1.dp.toPx()
    val horizStep = r * 1.5f
    val vertStep = r * kotlin.math.sqrt(3f)
    var col = 0
    var x = r
    while (x < w + r) {
        val yOffset = if (col % 2 == 0) 0f else vertStep / 2f
        var y = yOffset + r
        while (y < h + r) {
            drawHex(tint, Offset(x, y), r, strokeW)
            y += vertStep
        }
        x += horizStep
        col++
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHex(
    tint: Color, center: Offset, radius: Float, strokeW: Float,
) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = Math.toRadians((60.0 * i).toDouble())
        val px = center.x + radius * kotlin.math.cos(angle).toFloat()
        val py = center.y + radius * kotlin.math.sin(angle).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color = tint, style = Stroke(width = strokeW))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAuroraVeil() {
    drawRect(
        brush = PremiumBrushes.auroraHighlight(),
        alpha = 0.35f,
    )
}

