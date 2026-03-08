package com.worldmates.messenger.ui.premium

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import java.text.SimpleDateFormat
import java.util.*

private val GoldDark   = Color(0xFFB8860B)
private val GoldLight  = Color(0xFFFFD700)
private val GoldAccent = Color(0xFFFFF0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    viewModel: PremiumViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.premium_screen_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero section
            item {
                PremiumHero(isPro = state.isPro, proExpiresAt = state.proExpiresAt)
            }

            // Pricing cards (only for non-pro users)
            if (!state.isPro) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    PricingSection(
                        selected = state.selectedPlan,
                        onSelect = { viewModel.selectPlan(it) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.openPaymentPage() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldDark
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.premium_cta_trial),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.premium_trial_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { viewModel.openPaymentPage() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.premium_manage))
                    }
                }
            }

            // Feature list
            item {
                Spacer(modifier = Modifier.height(28.dp))
                FeatureList()
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PremiumHero(isPro: Boolean, proExpiresAt: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GoldDark.copy(alpha = 0.15f), Color.Transparent)
                )
            )
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(GoldLight, GoldDark))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "WorldMates PRO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = GoldDark
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (isPro && proExpiresAt > 0L) {
                val dateStr = remember(proExpiresAt) {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(proExpiresAt))
                }
                Text(
                    text = "PRO активний до $dateStr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldDark,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "worldmates.club",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun PricingSection(selected: PricingPlan, onSelect: (PricingPlan) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PricingCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.premium_monthly_label),
            price = stringResource(R.string.premium_monthly_price),
            badge = null,
            isSelected = selected == PricingPlan.MONTHLY,
            onClick = { onSelect(PricingPlan.MONTHLY) }
        )
        PricingCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.premium_yearly_label),
            price = stringResource(R.string.premium_yearly_price),
            badge = stringResource(R.string.premium_yearly_save),
            isSelected = selected == PricingPlan.YEARLY,
            onClick = { onSelect(PricingPlan.YEARLY) }
        )
    }
}

@Composable
private fun PricingCard(
    modifier: Modifier,
    title: String,
    price: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) GoldDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        label = "border"
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        label = "elevation"
    )

    Card(
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GoldDark.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (badge != null) {
                Surface(
                    color = GoldDark,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = price,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class FeatureItem(val icon: ImageVector, val resId: Int)

@Composable
private fun FeatureList() {
    val features = listOf(
        FeatureItem(Icons.Default.PeopleAlt,    R.string.premium_feature_accounts),
        FeatureItem(Icons.Default.AutoStories,  R.string.premium_feature_stories),
        FeatureItem(Icons.Default.Folder,       R.string.premium_feature_folders),
        FeatureItem(Icons.Default.AttachFile,   R.string.premium_feature_files),
        FeatureItem(Icons.Default.Palette,      R.string.premium_feature_themes),
        FeatureItem(Icons.Default.CloudUpload,  R.string.premium_feature_backup),
        FeatureItem(Icons.Default.BarChart,     R.string.premium_feature_analytics),
        FeatureItem(Icons.Default.Stars,        R.string.premium_feature_badge),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = "Що входить у PRO",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        features.forEach { feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(GoldLight.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        tint = GoldDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(feature.resId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
            if (features.last() != feature) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            }
        }
    }
}
