package com.worldmates.messenger.ui.channels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * Channel Premium Subscription Activity
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
            WorldMatesThemedApp {
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
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0D1A), Color(0xFF1C1C2E))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    text       = "Channel Premium",
                    color      = Color.White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f).padding(start = 8.dp)
                )
            }

            when (val state = uiState) {
                is ChannelPremiumUiState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE91E8C))
                    }
                }

                is ChannelPremiumUiState.Error -> {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF5252), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(state.message, color = Color.White, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.loadStatus(channelId) }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is ChannelPremiumUiState.Loaded -> {
                    val status = state.status

                    // Hero section
                    ChannelPremiumHero(channelName = channelName)
                    Spacer(Modifier.height(8.dp))

                    // Active subscription info
                    if (status.is_active == 1) {
                        ActiveSubscriptionBanner(status = status)
                        Spacer(Modifier.height(16.dp))
                    }

                    // Feature list
                    PremiumFeatureList()
                    Spacer(Modifier.height(20.dp))

                    if (isOwner) {
                        // Plan selector
                        Text(
                            "Choose a plan",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(12.dp))

                        val plans = status.plans ?: mapOf(
                            "monthly"   to ChannelPlanInfo(1, 299),
                            "quarterly" to ChannelPlanInfo(3, 807),
                            "annual"    to ChannelPlanInfo(12, 2691)
                        )

                        plans.forEach { (key, info) ->
                            ChannelPlanCard(
                                planKey    = key,
                                info       = info,
                                label      = ChannelPremiumViewModel.PLAN_LABELS[key] ?: key,
                                discount   = ChannelPremiumViewModel.PLAN_DISCOUNTS[key] ?: "",
                                selected   = selectedPlan == key,
                                onClick    = { selectedPlan = key }
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Spacer(Modifier.height(16.dp))

                        // Provider selector
                        Text(
                            "Payment method",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProviderChip("wayforpay", "Way4Pay", selectedProvider) { selectedProvider = it }
                            ProviderChip("liqpay", "LiqPay", selectedProvider) { selectedProvider = it }
                        }

                        Spacer(Modifier.height(24.dp))

                        val priceStr = plans[selectedPlan]?.let { "${it.price_uah} ₴" } ?: ""
                        Button(
                            onClick = { viewModel.createPayment(channelId, selectedPlan, selectedProvider) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E8C)),
                            shape  = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text       = "Subscribe for $priceStr",
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Non-owner: read-only info
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Only the channel owner can manage the Premium subscription.",
                                color       = Color.Gray,
                                textAlign   = TextAlign.Center,
                                fontSize    = 14.sp
                            )
                        }
                    }
                }

                is ChannelPremiumUiState.PaymentReady -> {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE91E8C))
                    }
                }
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun ChannelPremiumHero(channelName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFFE91E8C).copy(alpha = 0.35f), Color.Transparent)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("★", fontSize = 52.sp, color = Color(0xFFFFD700))
            Spacer(Modifier.height(8.dp))
            Text(
                text       = "Channel Premium",
                color      = Color.White,
                fontSize   = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text    = "Unlock full potential for «$channelName»",
                color   = Color.LightGray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun ActiveSubscriptionBanner(status: ChannelPremiumStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(Color(0xFF1B5E20), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text("Premium Active", color = Color(0xFF4CAF50), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Plan: ${status.plan?.replaceFirstChar { it.uppercase() }} · ${status.days_left} days remaining",
                color    = Color(0xFFA5D6A7),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PremiumFeatureList() {
    val features = listOf(
        Triple(Icons.Default.Videocam,       "1080p @ 60fps Livestreams", "Crystal-clear broadcasts"),
        Triple(Icons.Default.BarChart,       "Advanced Analytics",        "Detailed channel statistics"),
        Triple(Icons.Default.Star,           "Premium Badge",             "Stands out in channel lists"),
        Triple(Icons.Default.Notifications,  "Priority Notifications",    "Viewers see you first"),
        Triple(Icons.Default.HighQuality,    "HD Media Quality",          "Photos & videos in max quality")
    )

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        features.forEach { (icon, title, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(icon, null, tint = Color(0xFFE91E8C), modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(desc, color = Color.Gray, fontSize = 12.sp)
                }
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
    onClick: () -> Unit
) {
    val isPopular = planKey == "quarterly"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(
                if (selected) Color(0xFFE91E8C).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick  = null,
            colors   = RadioButtonDefaults.colors(selectedColor = Color(0xFFE91E8C))
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (isPopular) {
                    Text(
                        "POPULAR",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFE91E8C), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Text(discount, color = Color(0xFF4CAF50), fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${info.price_uah} ₴", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (info.months > 1) {
                Text("${info.price_uah / info.months} ₴/mo", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProviderChip(key: String, label: String, selected: String, onSelect: (String) -> Unit) {
    val active = key == selected
    Text(
        text     = label,
        color    = if (active) Color.White else Color.LightGray,
        fontSize = 14.sp,
        modifier = Modifier
            .background(
                if (active) Color(0xFFE91E8C) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onSelect(key) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
