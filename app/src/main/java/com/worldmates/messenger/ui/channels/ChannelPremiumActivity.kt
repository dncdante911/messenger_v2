package com.worldmates.messenger.ui.channels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.channels.premium.components.GlassSurface
import com.worldmates.messenger.ui.channels.premium.components.PremiumBadge
import com.worldmates.messenger.ui.channels.premium.components.PremiumBadgeSize
import com.worldmates.messenger.ui.channels.premium.components.PremiumDivider
import com.worldmates.messenger.ui.channels.premium.components.PremiumGlassIconButton
import com.worldmates.messenger.ui.channels.premium.components.PremiumPrimaryButton
import com.worldmates.messenger.ui.channels.premium.components.PremiumSectionHeader
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumTheme
import com.worldmates.messenger.ui.channels.premium.design.current
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.utils.LanguageManager

/**
 * Channel Premium Subscription Activity — Obsidian Gold redesign.
 *
 * Launched with:
 *   EXTRA_CHANNEL_ID   — channel page_id (Long)
 *   EXTRA_CHANNEL_NAME — display name (String)
 *   EXTRA_IS_OWNER     — true if the current user is the channel owner (Boolean)
 */
class ChannelPremiumActivity : AppCompatActivity() {

    private lateinit var viewModel: ChannelPremiumViewModel
    private var channelId: Long = 0

    companion object {
        const val EXTRA_CHANNEL_ID   = "channel_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_IS_OWNER     = "is_owner"

        fun createIntent(
            context: Context,
            channelId: Long,
            channelName: String,
            isOwner: Boolean
        ) = Intent(context, ChannelPremiumActivity::class.java).apply {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_IS_OWNER, isOwner)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initialize(this)

        channelId = intent.getLongExtra(EXTRA_CHANNEL_ID, 0)
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        val isOwner     = intent.getBooleanExtra(EXTRA_IS_OWNER, false)

        if (channelId == 0L) { finish(); return }

        viewModel = ViewModelProvider(this)[ChannelPremiumViewModel::class.java]
        viewModel.loadStatus(channelId)

        setContent {
            PremiumTheme {
                ChannelPremiumScreen(
                    viewModel   = viewModel,
                    channelId   = channelId,
                    channelName = channelName,
                    isOwner     = isOwner,
                    onPaymentUrl = { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onClose     = { finish() }
                )
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
private fun ChannelPremiumScreen(
    viewModel: ChannelPremiumViewModel,
    channelId: Long,
    channelName: String,
    isOwner: Boolean,
    onPaymentUrl: (String) -> Unit,
    onClose: () -> Unit
) {
    val design = PremiumDesign.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPlan by remember { mutableStateOf("monthly") }
    var selectedProvider by remember { mutableStateOf("wayforpay") }

    LaunchedEffect(uiState) {
        if (uiState is ChannelPremiumUiState.PaymentReady) {
            val resp = (uiState as ChannelPremiumUiState.PaymentReady).response
            val url = resp.invoice_url ?: resp.checkout_url
            if (url != null) onPaymentUrl(url)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBrushes.obsidianVertical()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // ── Top bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PremiumGlassIconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Outlined.ArrowBackIosNew,
                        contentDescription = stringResource(R.string.channel_premium_title),
                        tint = design.colors.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.channel_premium_title),
                    style = design.typography.titleSmall.copy(color = design.colors.onPrimary),
                )
            }

            when (val state = uiState) {
                is ChannelPremiumUiState.Loading -> PremiumLoadingBlock()

                is ChannelPremiumUiState.Error -> PremiumErrorBlock(
                    message = state.message,
                    onRetry = { viewModel.loadStatus(channelId) },
                )

                is ChannelPremiumUiState.Loaded -> {
                    val status = state.status

                    ChannelPremiumHero(channelName = channelName)
                    Spacer(Modifier.height(12.dp))

                    if (status.is_active == 1) {
                        ActiveSubscriptionBanner(status = status)
                        Spacer(Modifier.height(18.dp))
                    }

                    PremiumSectionHeader(
                        title = "BENEFITS",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    PremiumFeatureList()
                    Spacer(Modifier.height(20.dp))

                    if (isOwner) {
                        PremiumSectionHeader(
                            title = stringResource(R.string.channel_premium_choose_plan),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )

                        val plans = status.plans ?: mapOf(
                            "monthly"   to ChannelPlanInfo(1, 299),
                            "quarterly" to ChannelPlanInfo(3, 807),
                            "annual"    to ChannelPlanInfo(12, 2691),
                        )

                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            plans.forEach { (key, info) ->
                                val planLabel = when (key) {
                                    "monthly"   -> stringResource(R.string.plan_monthly)
                                    "quarterly" -> stringResource(R.string.plan_quarterly)
                                    "annual"    -> stringResource(R.string.plan_annual)
                                    else        -> key
                                }
                                val planDiscount = when (key) {
                                    "monthly"   -> stringResource(R.string.plan_discount_monthly)
                                    "quarterly" -> stringResource(R.string.plan_discount_quarterly)
                                    "annual"    -> stringResource(R.string.plan_discount_annual)
                                    else        -> ""
                                }
                                ChannelPlanCard(
                                    planKey  = key,
                                    info     = info,
                                    label    = planLabel,
                                    discount = planDiscount,
                                    selected = selectedPlan == key,
                                    onClick  = { selectedPlan = key },
                                )
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        PremiumSectionHeader(
                            title = stringResource(R.string.channel_premium_payment_method),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ProviderChip("wayforpay", "Way4Pay", selectedProvider) { selectedProvider = it }
                            ProviderChip("liqpay",    "LiqPay",  selectedProvider) { selectedProvider = it }
                        }

                        Spacer(Modifier.height(26.dp))

                        val priceStr = plans[selectedPlan]?.let { "${it.price_uah} ₴" } ?: ""
                        PremiumPrimaryButton(
                            text = stringResource(R.string.channel_premium_subscribe_btn, priceStr),
                            onClick = { viewModel.createPayment(channelId, selectedPlan, selectedProvider) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            leading = {
                                Icon(
                                    Icons.Outlined.Star,
                                    contentDescription = null,
                                    tint = design.colors.onAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    } else {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.channel_premium_owner_only),
                                style = design.typography.body.copy(color = design.colors.onSecondary),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                            )
                        }
                    }
                }

                is ChannelPremiumUiState.PaymentReady -> PremiumLoadingBlock()
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun PremiumLoadingBlock() {
    val design = PremiumDesign.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = design.colors.accent)
    }
}

@Composable
private fun PremiumErrorBlock(message: String, onRetry: () -> Unit) {
    val design = PremiumDesign.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = design.colors.danger,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                style = design.typography.body.copy(color = design.colors.onPrimary),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(16.dp))
            PremiumPrimaryButton(
                text = stringResource(R.string.channel_premium_retry),
                onClick = onRetry,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun ChannelPremiumHero(channelName: String) {
    val design = PremiumDesign.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PremiumBadge(size = PremiumBadgeSize.Hero)
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.channel_premium_title),
            style = design.typography.display.copy(color = design.colors.onPrimary),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.channel_premium_subtitle, channelName),
            style = design.typography.body.copy(color = design.colors.onMuted),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        PremiumDivider(modifier = Modifier.padding(horizontal = 48.dp))
    }
}

@Composable
private fun ActiveSubscriptionBanner(status: ChannelPremiumStatus) {
    val design = PremiumDesign.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = design.colors.success,
                modifier = Modifier.size(26.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.channel_premium_active_label),
                    style = design.typography.bodyStrong.copy(color = design.colors.success),
                )
                Text(
                    text = stringResource(
                        R.string.channel_premium_active_info,
                        status.plan?.replaceFirstChar { it.uppercase() } ?: "",
                        status.days_left,
                    ),
                    style = design.typography.caption.copy(color = design.colors.onSecondary),
                )
            }
        }
    }
}

@Composable
private fun PremiumFeatureList() {
    data class Feat(val icon: ImageVector, val title: String, val desc: String)
    val features = listOf(
        Feat(Icons.Outlined.Videocam,      stringResource(R.string.channel_premium_feat_stream),        stringResource(R.string.channel_premium_feat_stream_desc)),
        Feat(Icons.Outlined.BarChart,      stringResource(R.string.channel_premium_feat_analytics),     stringResource(R.string.channel_premium_feat_analytics_desc)),
        Feat(Icons.Outlined.Star,          stringResource(R.string.channel_premium_feat_badge),         stringResource(R.string.channel_premium_feat_badge_desc)),
        Feat(Icons.Outlined.Notifications, stringResource(R.string.channel_premium_feat_notifications), stringResource(R.string.channel_premium_feat_notifications_desc)),
        Feat(Icons.Outlined.HighQuality,   stringResource(R.string.channel_premium_feat_hd),            stringResource(R.string.channel_premium_feat_hd_desc)),
        Feat(Icons.Outlined.Bolt,          "Premium reactions", "Gold reactions exclusive to your subscribers."),
    )

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        features.forEach { f -> PremiumFeatureRow(f.icon, f.title, f.desc) }
    }
}

@Composable
private fun PremiumFeatureRow(
    icon: ImageVector,
    title: String,
    desc: String,
) {
    val design = PremiumDesign.current
    GlassSurface(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PremiumBrushes.matteGoldLinear(), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = design.colors.onAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = design.typography.bodyStrong.copy(color = design.colors.onPrimary),
                )
                Text(
                    text = desc,
                    style = design.typography.caption.copy(color = design.colors.onMuted),
                )
            }
        }
    }
}

@Composable
private fun ChannelPlanCard(
    planKey: String,
    info: ChannelPlanInfo,
    label: String,
    discount: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val design = PremiumDesign.current
    val isPopular = planKey == "quarterly"
    val shape = RoundedCornerShape(16.dp)
    val borderWidth by animateDpAsState(if (selected) 1.2.dp else 0.6.dp, label = "planBorder")
    val bg by animateColorAsState(
        if (selected) design.colors.glassFillStrong else design.colors.glassFill,
        label = "planBg",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .then(
                if (selected) Modifier.background(PremiumBrushes.auroraHighlight(), shape) else Modifier,
            )
            .border(
                width = borderWidth,
                brush = if (selected) PremiumBrushes.matteGoldLinear() else PremiumBrushes.goldHairline(),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) design.colors.accent else design.colors.glassFill,
                        RoundedCornerShape(999.dp),
                    )
                    .border(
                        width = 1.dp,
                        brush = PremiumBrushes.goldHairline(),
                        shape = RoundedCornerShape(999.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(design.colors.onAccent, RoundedCornerShape(999.dp)),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = design.typography.bodyStrong.copy(color = design.colors.onPrimary),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPopular) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PremiumBrushes.matteGoldLinear(), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.channel_premium_popular_badge),
                                style = design.typography.overline.copy(color = design.colors.onAccent),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = discount,
                    style = design.typography.caption.copy(color = design.colors.success),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${info.price_uah} ₴",
                    style = design.typography.metric.copy(color = design.colors.onPrimary),
                )
                if (info.months > 1) {
                    Text(
                        text = stringResource(R.string.plan_price_per_month, "${info.price_uah / info.months} ₴"),
                        style = design.typography.caption.copy(color = design.colors.onMuted),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(key: String, label: String, selected: String, onSelect: (String) -> Unit) {
    val design = PremiumDesign.current
    val active = key == selected
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (active) design.colors.glassFillStrong else design.colors.glassFill,
                shape,
            )
            .border(
                width = if (active) 1.dp else 0.6.dp,
                brush = if (active) PremiumBrushes.matteGoldLinear() else PremiumBrushes.goldHairline(),
                shape = shape,
            )
            .clickable { onSelect(key) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = design.typography.button.copy(
                color = if (active) design.colors.accent else design.colors.onSecondary,
            ),
        )
    }
}
