package com.worldmates.messenger.ui.stars

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.network.StarsPack
import com.worldmates.messenger.network.StarsTransaction
import java.text.SimpleDateFormat
import java.util.*

// ─── Brand colors ─────────────────────────────────────────────────────────────
private val StarGold      = Color(0xFFFFD700)
private val StarGoldDark  = Color(0xFFB8860B)
private val StarGoldDeep  = Color(0xFF7B5800)
private val StarGoldLight = Color(0xFFFFF8DC)
private val StarAmber     = Color(0xFFFF8C00)

// ─── Root screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarsScreen(viewModel: StarsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Error snackbar
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Send success snackbar
    LaunchedEffect(state.sendSuccess) {
        if (state.sendSuccess) {
            snackbarHostState.showSnackbar("⭐ Зірки надіслано!")
            viewModel.clearSendSuccess()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.stars_tab_balance),
        stringResource(R.string.stars_tab_topup),
        stringResource(R.string.stars_tab_send),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", fontSize = 20.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.stars_title),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Balance card (always visible) ─────────────────────────────────
            BalanceCard(
                balance       = state.balance,
                totalPurchased = state.totalPurchased,
                totalSent     = state.totalSent,
                totalReceived = state.totalReceived,
                isLoading     = state.isLoading,
            )

            // ── Tab bar ────────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.Transparent,
                contentColor     = StarGold,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }

            // ── Tab content ────────────────────────────────────────────────────
            when (selectedTab) {
                0 -> TransactionsTab(
                    transactions = state.transactions,
                    isLoading    = state.isLoading,
                    onLoadMore   = { viewModel.loadMoreTransactions() },
                )
                1 -> TopUpTab(
                    packs        = state.packs,
                    selectedPack = state.selectedPack,
                    isLoading    = state.isLoading,
                    onSelectPack = { viewModel.selectPack(it) },
                    onPay        = { provider -> viewModel.purchase(provider) },
                )
                2 -> SendTab(
                    balance   = state.balance,
                    isSending = state.isSending,
                    onSend    = { toUserId, amount, note -> viewModel.sendStars(toUserId, amount, note) },
                )
            }
        }
    }
}

// ─── Balance card ─────────────────────────────────────────────────────────────
@Composable
private fun BalanceCard(
    balance: Int,
    totalPurchased: Int,
    totalSent: Int,
    totalReceived: Int,
    isLoading: Boolean,
) {
    val animBalance by animateIntAsState(
        targetValue   = balance,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label         = "balance"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(StarGoldDeep, StarGoldDark, StarAmber))
            )
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("⭐", fontSize = 48.sp)
            Spacer(Modifier.height(4.dp))
            if (isLoading && balance == 0) {
                CircularProgressIndicator(color = StarGold, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            } else {
                Text(
                    text       = "$animBalance",
                    fontSize   = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color.White,
                )
            }
            Text(
                text     = stringResource(R.string.stars_balance_label),
                fontSize = 14.sp,
                color    = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(16.dp))
            // Stats row
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(label = stringResource(R.string.stars_stat_purchased), value = totalPurchased)
                StatItem(label = stringResource(R.string.stars_stat_sent),      value = totalSent)
                StatItem(label = stringResource(R.string.stars_stat_received),  value = totalReceived)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$value ⭐", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

// ─── Transactions tab ─────────────────────────────────────────────────────────
@Composable
private fun TransactionsTab(
    transactions: List<StarsTransaction>,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
) {
    if (transactions.isEmpty() && !isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⭐", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.stars_no_transactions),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier          = Modifier.fillMaxSize(),
        contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(transactions, key = { it.id }) { tx ->
            TransactionItem(tx)
        }
        if (transactions.isNotEmpty()) {
            item {
                TextButton(
                    onClick  = onLoadMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.stars_load_more))
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(tx: StarsTransaction) {
    val isIncoming = tx.type == "receive" || tx.type == "purchase" || tx.type == "refund"
    val amountColor = if (isIncoming) Color(0xFF2ECC71) else StarAmber
    val icon = when (tx.type) {
        "purchase" -> Icons.Outlined.ShoppingCart
        "send"     -> Icons.Outlined.Send
        "receive"  -> Icons.Outlined.Star
        "refund"   -> Icons.Outlined.Replay
        else       -> Icons.Outlined.Star
    }
    val sign = if (isIncoming) "+" else "−"

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier           = Modifier.padding(12.dp),
            verticalAlignment  = Alignment.CenterVertically,
        ) {
            // Icon
            Box(
                modifier        = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isIncoming) Color(0xFF2ECC71).copy(0.15f) else StarAmber.copy(0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = amountColor, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            // Description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = txTitle(tx),
                    fontWeight = FontWeight.Medium,
                    fontSize   = 14.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                tx.note?.let { note ->
                    Text(
                        text     = note,
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text     = formatDate(tx.createdAt),
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            // Amount
            Text(
                text       = "$sign${tx.amount} ⭐",
                color      = amountColor,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
            )
        }
    }
}

private fun txTitle(tx: StarsTransaction): String = when (tx.type) {
    "purchase" -> "Поповнення"
    "send"     -> "Надіслано: ${tx.otherUserName ?: "користувачу"}"
    "receive"  -> "Отримано від: ${tx.otherUserName ?: "користувача"}"
    "refund"   -> "Повернення"
    else       -> tx.type
}

private fun formatDate(raw: String): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val date = sdf.parse(raw) ?: return raw
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(date)
} catch (_: Exception) { raw }

// ─── Top-Up tab ───────────────────────────────────────────────────────────────
@Composable
private fun TopUpTab(
    packs:        List<StarsPack>,
    selectedPack: StarsPack?,
    isLoading:    Boolean,
    onSelectPack: (StarsPack) -> Unit,
    onPay:        (String) -> Unit,
) {
    LazyColumn(
        modifier          = Modifier.fillMaxSize(),
        contentPadding    = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.stars_choose_pack),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.stars_pack_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        items(packs, key = { it.id }) { pack ->
            PackCard(
                pack       = pack,
                isSelected = pack.id == selectedPack?.id,
                onClick    = { onSelectPack(pack) },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            if (selectedPack != null) {
                Text(
                    text  = stringResource(R.string.stars_pay_via),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PayButton(
                        label     = "Way4Pay",
                        enabled   = !isLoading,
                        isLoading = isLoading,
                        modifier  = Modifier.weight(1f),
                        onClick   = { onPay("wayforpay") },
                    )
                    PayButton(
                        label     = "LiqPay",
                        enabled   = !isLoading,
                        isLoading = false,
                        modifier  = Modifier.weight(1f),
                        onClick   = { onPay("liqpay") },
                    )
                }
            }
        }
    }
}

@Composable
private fun PackCard(pack: StarsPack, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) StarGold else Color.Transparent
    val bgColor = if (isSelected) StarGoldLight.copy(alpha = 0.15f) else Color.Transparent

    Card(
        shape   = RoundedCornerShape(14.dp),
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() },
    ) {
        Row(
            modifier          = Modifier
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⭐", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = "${pack.stars} ${stringResource(R.string.stars_unit)}",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 17.sp,
                    )
                    if (pack.isPopular) {
                        Spacer(Modifier.width(8.dp))
                        Badge(containerColor = StarGold) {
                            Text(
                                stringResource(R.string.stars_popular),
                                color    = Color.Black,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    text  = pack.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text       = "${pack.priceUah} грн",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = if (isSelected) StarGoldDark else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PayButton(
    label: String,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier,
        colors   = ButtonDefaults.buttonColors(containerColor = StarGoldDark),
        shape    = RoundedCornerShape(12.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color       = Color.White,
                strokeWidth = 2.dp,
                modifier    = Modifier.size(18.dp),
            )
        } else {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Send tab ─────────────────────────────────────────────────────────────────
@Composable
private fun SendTab(
    balance:  Int,
    isSending: Boolean,
    onSend:   (toUserId: Int, amount: Int, note: String?) -> Unit,
) {
    var userIdInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var noteInput   by remember { mutableStateOf("") }
    var userIdError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    LazyColumn(
        modifier          = Modifier.fillMaxSize(),
        contentPadding    = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.stars_send_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = stringResource(R.string.stars_send_desc, balance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            OutlinedTextField(
                value         = userIdInput,
                onValueChange = { userIdInput = it.filter { c -> c.isDigit() }; userIdError = false },
                label         = { Text(stringResource(R.string.stars_recipient_id)) },
                leadingIcon   = { Icon(Icons.Outlined.Person, contentDescription = null) },
                isError       = userIdError,
                supportingText = if (userIdError) { { Text(stringResource(R.string.stars_error_recipient)) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value         = amountInput,
                onValueChange = { amountInput = it.filter { c -> c.isDigit() }; amountError = false },
                label         = { Text(stringResource(R.string.stars_amount_label)) },
                leadingIcon   = { Text("⭐", modifier = Modifier.padding(start = 12.dp)) },
                isError       = amountError,
                supportingText = if (amountError) { { Text(stringResource(R.string.stars_error_amount)) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value         = noteInput,
                onValueChange = { if (it.length <= 255) noteInput = it },
                label         = { Text(stringResource(R.string.stars_note_label)) },
                leadingIcon   = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
        }

        item {
            // Quick amount chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 25, 50, 100).forEach { preset ->
                    FilterChip(
                        selected = amountInput == "$preset",
                        onClick  = { amountInput = "$preset"; amountError = false },
                        label    = { Text("$preset ⭐") },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StarGold.copy(alpha = 0.2f),
                            selectedLabelColor     = StarGoldDeep,
                        ),
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val uid = userIdInput.toIntOrNull()
                    val amt = amountInput.toIntOrNull()
                    userIdError = uid == null || uid <= 0
                    amountError = amt == null || amt < 1 || amt > balance
                    if (!userIdError && !amountError) {
                        onSend(uid!!, amt!!, noteInput.ifBlank { null })
                    }
                },
                enabled  = !isSending,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = StarGoldDark),
                shape    = RoundedCornerShape(12.dp),
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stars_send_button), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
