package com.worldmates.messenger.ui.business

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.BusinessProfile

// ─── Brand Colors ─────────────────────────────────────────────────────────────
private val BizDeep    = Color(0xFF0D1B2A)
private val BizDark    = Color(0xFF1A2942)
private val BizMid     = Color(0xFF243B55)
private val BizAccent  = Color(0xFF1E90FF)
private val BizGold    = Color(0xFFFFD166)
private val BizSurface = Color(0xFF1E2D40)
private val BizCard    = Color(0xFF233044)

// ─── Navigation enum ──────────────────────────────────────────────────────────
sealed class BusinessScreen {
    object Dashboard     : BusinessScreen()
    object Profile       : BusinessScreen()
    object Hours         : BusinessScreen()
    object AutoReply     : BusinessScreen()
    object Greeting      : BusinessScreen()
    object QuickReplies  : BusinessScreen()
    object Links         : BusinessScreen()
    object Stats         : BusinessScreen()
    object Verification  : BusinessScreen()
    object ApiAccess     : BusinessScreen()
}

// ─── Root composable ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessModeScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var currentScreen by remember { mutableStateOf<BusinessScreen>(BusinessScreen.Dashboard) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.successMsg) {
        state.successMsg?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BizDeep)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = Color.Transparent
        ) { padding ->
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                },
                label = "biz_nav"
            ) { screen ->
                when (screen) {
                    BusinessScreen.Dashboard -> DashboardScreen(
                        state     = state,
                        onBack    = onBack,
                        onNavigate = { currentScreen = it },
                        padding   = padding
                    )
                    BusinessScreen.Profile -> BusinessProfileEditScreen(
                        state     = state,
                        onSave    = { viewModel.saveProfile(it) },
                        onBack    = { currentScreen = BusinessScreen.Dashboard }
                    )
                    BusinessScreen.Hours -> BusinessHoursScreen(
                        hours     = state.hours,
                        onSave    = { viewModel.saveHours(it) },
                        onBack    = { currentScreen = BusinessScreen.Dashboard }
                    )
                    BusinessScreen.AutoReply -> AutoReplyScreen(
                        state     = state,
                        onSave    = { viewModel.saveProfile(it) },
                        onBack    = { currentScreen = BusinessScreen.Dashboard }
                    )
                    BusinessScreen.Greeting -> GreetingScreen(
                        state     = state,
                        onSave    = { viewModel.saveProfile(it) },
                        onBack    = { currentScreen = BusinessScreen.Dashboard }
                    )
                    BusinessScreen.QuickReplies -> QuickRepliesScreen(
                        state       = state,
                        onCreate    = { s, t -> viewModel.createQuickReply(s, t) },
                        onUpdate    = { id, s, t -> viewModel.updateQuickReply(id, s, t) },
                        onDelete    = { viewModel.deleteQuickReply(it) },
                        onBack      = { currentScreen = BusinessScreen.Dashboard }
                    )
                    BusinessScreen.Links -> BusinessLinksScreen(
                        state     = state,
                        onCreate  = { t, p -> viewModel.createLink(t, p) },
                        onDelete  = { viewModel.deleteLink(it) },
                        onBack    = { currentScreen = BusinessScreen.Dashboard }
                    )
                    BusinessScreen.Stats -> BusinessStatsScreen(
                        state       = state,
                        onBack      = { currentScreen = BusinessScreen.Dashboard },
                        onLoadStats = { days -> viewModel.loadStats(days) },
                    )
                    BusinessScreen.Verification -> VerificationScreen(
                        state     = state,
                        onBack    = { currentScreen = BusinessScreen.Dashboard },
                        onRequest = { viewModel.requestVerification() },
                    )
                    BusinessScreen.ApiAccess -> ApiAccessScreen(
                        state      = state,
                        onBack     = { currentScreen = BusinessScreen.Dashboard },
                        onGenerate = { viewModel.generateApiKey() },
                        onRevoke   = { viewModel.revokeApiKey() },
                        onLoadKey  = { viewModel.loadApiKey() },
                    )
                }
            }
        }
    }
}

// ─── Dashboard ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    state: BusinessUiState,
    onBack: () -> Unit,
    onNavigate: (BusinessScreen) -> Unit,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(BizMid, BizDark))
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        stringResource(R.string.biz_mode_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        if (state.profile != null)
                            state.profile.businessName ?: stringResource(R.string.biz_active)
                        else
                            stringResource(R.string.biz_not_configured),
                        color = BizGold,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.profile != null) {
                    Icon(Icons.Default.Business, null, tint = BizGold, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BizAccent)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status card
            item {
                BizStatusCard(profile = state.profile)
            }

            item { BizSectionHeader(stringResource(R.string.biz_section_profile)) }

            item {
                BizMenuItem(
                    icon    = Icons.Default.Store,
                    title   = stringResource(R.string.biz_profile_title),
                    subtitle = stringResource(R.string.biz_profile_subtitle),
                    badge   = if (state.profile?.businessName != null) stringResource(R.string.biz_configured) else null,
                    onClick  = { onNavigate(BusinessScreen.Profile) }
                )
            }
            item {
                BizMenuItem(
                    icon    = Icons.Default.Schedule,
                    title   = stringResource(R.string.biz_hours_title),
                    subtitle = stringResource(R.string.biz_hours_subtitle),
                    onClick  = { onNavigate(BusinessScreen.Hours) }
                )
            }

            item { BizSectionHeader(stringResource(R.string.biz_section_messages)) }

            item {
                BizMenuItem(
                    icon    = Icons.Default.ReplyAll,
                    title   = stringResource(R.string.biz_auto_reply_title),
                    subtitle = stringResource(R.string.biz_auto_reply_subtitle),
                    badge   = if (state.profile?.autoReplyEnabled == 1) stringResource(R.string.biz_on) else null,
                    badgeColor = BizAccent,
                    onClick  = { onNavigate(BusinessScreen.AutoReply) }
                )
            }
            item {
                BizMenuItem(
                    icon    = Icons.Default.EmojiPeople,
                    title   = stringResource(R.string.biz_greeting_title),
                    subtitle = stringResource(R.string.biz_greeting_subtitle),
                    badge   = if (state.profile?.greetingEnabled == 1) stringResource(R.string.biz_on) else null,
                    badgeColor = BizAccent,
                    onClick  = { onNavigate(BusinessScreen.Greeting) }
                )
            }
            item {
                BizMenuItem(
                    icon    = Icons.Default.Quickreply,
                    title   = stringResource(R.string.biz_quick_replies_title),
                    subtitle = stringResource(R.string.biz_quick_replies_count, state.quickReplies.size),
                    onClick  = { onNavigate(BusinessScreen.QuickReplies) }
                )
            }

            item { BizSectionHeader(stringResource(R.string.biz_section_tools)) }

            item {
                BizMenuItem(
                    icon    = Icons.Default.Link,
                    title   = stringResource(R.string.biz_links_title),
                    subtitle = stringResource(R.string.biz_links_count, state.links.size),
                    onClick  = { onNavigate(BusinessScreen.Links) }
                )
            }

            item { BizSectionHeader(stringResource(R.string.biz_section_creator)) }

            item {
                BizMenuItem(
                    icon     = Icons.Default.BarChart,
                    title    = stringResource(R.string.biz_stats_title),
                    subtitle = stringResource(R.string.biz_stats_subtitle),
                    onClick  = { onNavigate(BusinessScreen.Stats) }
                )
            }
            item {
                val verStatus = state.profile?.verificationStatus ?: "none"
                BizMenuItem(
                    icon      = Icons.Default.Verified,
                    title     = stringResource(R.string.biz_verified_title),
                    subtitle  = stringResource(R.string.biz_verified_subtitle),
                    badge     = when (verStatus) {
                        "approved" -> "✅"
                        "pending"  -> "⏳"
                        else       -> null
                    },
                    badgeColor = when (verStatus) {
                        "approved" -> Color(0xFF2ECC71)
                        "pending"  -> BizGold
                        else       -> BizAccent
                    },
                    onClick   = { onNavigate(BusinessScreen.Verification) }
                )
            }
            item {
                BizMenuItem(
                    icon     = Icons.Default.Key,
                    title    = stringResource(R.string.biz_api_title),
                    subtitle = stringResource(R.string.biz_api_subtitle),
                    badge    = if (state.apiKey != null) stringResource(R.string.biz_api_active) else null,
                    badgeColor = BizAccent,
                    onClick  = { onNavigate(BusinessScreen.ApiAccess) }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── Status card ──────────────────────────────────────────────────────────────
@Composable
private fun BizStatusCard(profile: BusinessProfile?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = BizSurface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (profile != null) BizAccent.copy(alpha = 0.2f)
                        else Color.Gray.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (profile != null) Icons.Default.Business else Icons.Default.BusinessCenter,
                    null,
                    tint = if (profile != null) BizAccent else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (profile != null) stringResource(R.string.biz_status_active)
                    else stringResource(R.string.biz_status_inactive),
                    color = if (profile != null) BizGold else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    if (profile != null)
                        profile.businessName ?: stringResource(R.string.biz_no_name)
                    else
                        stringResource(R.string.biz_configure_hint),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── Section header ───────────────────────────────────────────────────────────
@Composable
private fun BizSectionHeader(title: String) {
    Text(
        title,
        color     = BizGold,
        fontSize  = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier  = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

// ─── Menu item ────────────────────────────────────────────────────────────────
@Composable
private fun BizMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = BizGold,
    onClick: () -> Unit
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = BizCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = BizAccent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape  = RoundedCornerShape(8.dp),
                    color  = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        badge,
                        color    = badgeColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
        }
    }
}
