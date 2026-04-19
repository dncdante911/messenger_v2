package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/** A gift-able plan on the sheet. */
data class GiftPlanOption(
    val key: String,
    val months: Int,
    val priceUah: Int,
    val label: String,
    val discountLabel: String? = null,
)

/**
 * Sheet for gifting a premium subscription to another user. The caller
 * supplies the plan catalog, recipient handle input state, and the
 * [onGift] callback that fires a server call.
 *
 * Intentionally payment-provider agnostic — the parent activity chooses
 * which provider to use when the gift is confirmed.
 */
@Composable
fun GiftSubscriptionSheet(
    plans: List<GiftPlanOption>,
    onGift: (recipient: String, planKey: String, note: String) -> Unit,
    modifier: Modifier = Modifier,
    initialRecipient: String = "",
    title: String = "Gift a premium subscription",
) {
    val design = PremiumDesign.current
    var recipient by remember { mutableStateOf(initialRecipient) }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(plans.firstOrNull()?.key.orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(design.colors.backgroundElevated, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(design.colors.glassStroke),
        )

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PremiumBrushes.matteGoldLinear()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CardGiftcard,
                    contentDescription = null,
                    tint = design.colors.onAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = title,
                    style = design.typography.titleSmall.copy(color = design.colors.onPrimary),
                )
                Text(
                    text = "Paid once, applied to their channel.",
                    style = design.typography.caption.copy(color = design.colors.onMuted),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        PremiumSectionHeader(title = "RECIPIENT")
        GiftTextField(
            value = recipient,
            onValueChange = { recipient = it },
            placeholder = "@username or channel id",
            keyboardType = KeyboardType.Text,
        )

        Spacer(Modifier.height(12.dp))

        PremiumSectionHeader(title = "PLAN")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plans.forEach { plan ->
                GiftPlanRow(
                    plan = plan,
                    selected = plan.key == selected,
                    onClick = { selected = plan.key },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        PremiumSectionHeader(title = "NOTE (OPTIONAL)")
        GiftTextField(
            value = note,
            onValueChange = { note = it.take(140) },
            placeholder = "Say something nice…",
            keyboardType = KeyboardType.Text,
            minHeight = 72.dp,
        )

        Spacer(Modifier.height(20.dp))

        val priceLabel = plans.firstOrNull { it.key == selected }?.let { "${it.priceUah} ₴" }.orEmpty()
        PremiumPrimaryButton(
            text = if (priceLabel.isNotEmpty()) "Send gift · $priceLabel" else "Send gift",
            onClick = { onGift(recipient.trim(), selected, note.trim()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = recipient.isNotBlank() && selected.isNotBlank(),
        )

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun GiftPlanRow(
    plan: GiftPlanOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val design = PremiumDesign.current
    val shape = RoundedCornerShape(design.shapes.cornerMedium)
    val border = if (selected) design.colors.accent else design.colors.glassStroke
    val fill = if (selected) design.colors.reactionSelectedFill else design.colors.glassFill
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill, shape)
            .border(width = if (selected) 1.dp else 0.5.dp, color = border, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.label,
                style = design.typography.bodyStrong.copy(color = design.colors.onPrimary),
            )
            plan.discountLabel?.let {
                Text(
                    text = it,
                    style = design.typography.caption.copy(color = design.colors.accent),
                )
            }
        }
        Text(
            text = "${plan.priceUah} ₴",
            style = design.typography.metric.copy(color = if (selected) design.colors.accent else design.colors.onPrimary),
        )
    }
}

@Composable
private fun GiftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    minHeight: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val design = PremiumDesign.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(design.colors.glassFill, shape)
            .border(width = 0.5.dp, color = design.colors.glassStroke, shape = shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = design.typography.body.copy(color = design.colors.onMuted),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = minHeight <= 48.dp,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(design.colors.accent),
            textStyle = design.typography.body.copy(color = design.colors.onPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight - 24.dp),
        )
    }
}
