package com.worldmates.messenger.ui.premium

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

// ─── Brand colors ─────────────────────────────────────────────────────────────
private val Gold      = Color(0xFFFFD700)
private val GoldDark  = Color(0xFFB8860B)
private val GoldDeep  = Color(0xFF7B5800)
private val GoldLight = Color(0xFFFFF9E0)

// ─── Feature data ─────────────────────────────────────────────────────────────
private data class Feature(val icon: ImageVector, val title: String, val subtitle: String)

private val FEATURES = listOf(
    Feature(Icons.Default.PeopleAlt,   "До 10 акаунтів",           "Безкоштовно — лише 5. З PRO — до 10 паралельних акаунтів."),
    Feature(Icons.Default.Folder,      "До 50 папок чатів",        "Безкоштовно — 10 папок. PRO — до 50 для ідеального порядку."),
    Feature(Icons.Default.PhotoCamera, "До 25 аватарів",           "Безкоштовно — 10 фото. PRO — до 25 + анімовані (GIF) аватари."),
    Feature(Icons.Default.AutoStories, "До 25 Stories на добу",    "Безкоштовно — 5 сторіз (30 сек відео / 24 год). PRO — 25 сторіз (60 сек / 48 год)."),
    Feature(Icons.Default.Mic,         "Голосові до 60 хвилин",    "Безкоштовно — до 15 хв. PRO — до 1 години запису."),
    Feature(Icons.Default.GraphicEq,   "Транскрипція голосових",   "Автоматичний текстовий переклад голосових повідомлень (PRO-ексклюзив)."),
    Feature(Icons.Default.EmojiEmotions,"Кастомний emoji-статус",  "Встановлюй унікальні emoji замість стандартного статусу."),
    Feature(Icons.Default.PushPin,     "До 15 закріплених",        "Безкоштовно — 5 закріплених повідомлень. PRO — до 15."),
    Feature(Icons.Default.AttachFile,  "Файли до 1 ГБ",            "Надсилай великі відео та архіви без обмежень за розміром."),
    Feature(Icons.Default.Palette,     "Ексклюзивні теми",         "Унікальні колірні схеми та анімовані фони, доступні тільки PRO."),
    Feature(Icons.Default.Stars,       "PRO-значок у профілі",     "Градієнтна рамка навколо аватара, що підкреслює твій статус."),
    Feature(Icons.Default.Block,       "Без реклами",              "Жодного рекламного контенту у стрічці та Stories."),
)

// ─── Root composable ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(viewModel: PremiumViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("WallyMates PRO", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
            // ── Hero ──────────────────────────────────────────────────────────
            item { HeroSection(isPro = state.isPro, proExpiresAt = state.proExpiresAt, daysLeft = state.daysLeft) }

            // ── Current subscriber — just manage button ────────────────────
            if (state.isPro) {
                item {
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { viewModel.syncSubscription() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Оновити статус підписки")
                    }
                }
            } else {
                // ── Slider + pricing ──────────────────────────────────────
                item {
                    Spacer(Modifier.height(12.dp))
                    MonthSlider(
                        months       = state.months,
                        amountUah    = state.amountUah,
                        perMonthUah  = state.perMonthUah,
                        onMonthsChange = { viewModel.setMonths(it) }
                    )
                }
                // ── Payment buttons ───────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    PaymentButtons(
                        isLoading   = state.isLoading,
                        amountUah   = state.amountUah,
                        months      = state.months,
                        onWayForPay = { viewModel.pay(PaymentProvider.WAYFORPAY) },
                        onLiqPay    = { viewModel.pay(PaymentProvider.LIQPAY) },
                        onMonoBank  = { viewModel.pay(PaymentProvider.MONOBANK) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Одноразовий платіж. Без авторекурентних списань.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Features ─────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(28.dp))
                FeatureListSection()
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ─── Hero ─────────────────────────────────────────────────────────────────────
@Composable
private fun HeroSection(isPro: Boolean, proExpiresAt: Long, daysLeft: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(GoldDark.copy(0.18f), Color.Transparent)))
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Gold, GoldDark))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Stars, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("WallyMates PRO", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold, color = GoldDark)
            Spacer(Modifier.height(6.dp))
            if (isPro && proExpiresAt > 0L) {
                val dateStr = remember(proExpiresAt) {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(proExpiresAt))
                }
                Text("✓ Активний до $dateStr ($daysLeft дн.)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldDark, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Розблокуй всі можливості",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}

// ─── Slider ───────────────────────────────────────────────────────────────────
@Composable
private fun MonthSlider(
    months: Int,
    amountUah: Int,
    perMonthUah: Int,
    onMonthsChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // Title + months
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Термін підписки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val scale by animateFloatAsState(targetValue = 1f, animationSpec = spring(), label = "scale")
            Box(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GoldDark.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = pluralMonths(months),
                    color = GoldDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Slider(
            value = months.toFloat(),
            onValueChange = { onMonthsChange(it.toInt().coerceIn(1, 24)) },
            valueRange = 1f..24f,
            steps = 22, // 24-1-1 = 22 internal steps
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor       = GoldDark,
                activeTrackColor = GoldDark
            )
        )

        // Min / max labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1 місяць", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Text("2 роки", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }

        Spacer(Modifier.height(16.dp))

        // Price breakdown card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = GoldLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("До сплати", style = MaterialTheme.typography.labelMedium,
                        color = GoldDeep.copy(alpha = 0.7f))
                    Text("$amountUah ₴", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold, color = GoldDeep)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("За місяць", style = MaterialTheme.typography.labelMedium,
                        color = GoldDeep.copy(alpha = 0.7f))
                    Text("$perMonthUah ₴/міс", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = GoldDeep)
                    if (months >= 2) {
                        val saved = PremiumViewModel.BASE_PRICE_UAH * months - amountUah
                        Text("економія $saved ₴", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

// ─── Payment buttons ──────────────────────────────────────────────────────────
@Composable
private fun PaymentButtons(
    isLoading: Boolean,
    amountUah: Int,
    months: Int,
    onWayForPay: () -> Unit,
    onLiqPay: () -> Unit,
    onMonoBank: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Way4Pay — primary button
        Button(
            onClick = onWayForPay,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldDark)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Way4Pay  •  $amountUah ₴",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // LiqPay — secondary button
        val liqPayGreen = Color(0xFF00A651)
        OutlinedButton(
            onClick = onLiqPay,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, liqPayGreen)
        ) {
            Icon(Icons.Default.CreditCard, contentDescription = null, tint = liqPayGreen)
            Spacer(Modifier.width(8.dp))
            Text("LiqPay  •  $amountUah ₴",
                color = liqPayGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        // Monobank — tertiary button
        val monoBlack = Color(0xFF1A1A1A)
        OutlinedButton(
            onClick = onMonoBank,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, monoBlack)
        ) {
            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = monoBlack)
            Spacer(Modifier.width(8.dp))
            Text("Monobank  •  $amountUah ₴",
                color = monoBlack, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ─── Feature list ─────────────────────────────────────────────────────────────
@Composable
private fun FeatureListSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text("Що входить у PRO",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp))

        FEATURES.forEachIndexed { idx, feature ->
            FeatureRow(feature)
            if (idx < FEATURES.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: Feature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GoldDark.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, contentDescription = null, tint = GoldDark, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(feature.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(feature.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Icon(Icons.Default.CheckCircle, contentDescription = null,
            tint = GoldDark, modifier = Modifier.size(18.dp))
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
private fun pluralMonths(n: Int): String = when {
    n == 1          -> "1 місяць"
    n in 2..4       -> "$n місяці"
    n in 5..20      -> "$n місяців"
    n % 10 == 1     -> "$n місяць"
    n % 10 in 2..4  -> "$n місяці"
    else            -> "$n місяців"
}
