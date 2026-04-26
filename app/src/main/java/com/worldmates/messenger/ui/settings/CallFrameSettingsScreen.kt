package com.worldmates.messenger.ui.settings

import android.content.Context
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.calls.CallBackground
import com.worldmates.messenger.ui.calls.CallBackgroundManager
import com.worldmates.messenger.ui.calls.CallBgLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallFrameSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(CallBackgroundManager.load(context)) }
    val isPremiumUser = remember { UserSession.isProActive }

    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.call_bg_reset_default)) },
            text = { Text(stringResource(R.string.call_bg_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    CallBackgroundManager.reset(context)
                    selected = CallBackground.DEFAULT
                    showResetDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.call_bg_settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0084FF),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.call_bg_settings_desc),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // ── Free section ─────────────────────────────────────────────────
            item {
                SectionHeader(label = stringResource(R.string.call_bg_free_label))
            }

            val freeItems = CallBackground.values().filter { !it.isPremium }
            items(freeItems.size) { idx ->
                val bg = freeItems[idx]
                CallBgCard(
                    bg = bg,
                    isSelected = selected == bg,
                    isLocked = false,
                    onClick = {
                        selected = bg
                        CallBackgroundManager.save(context, bg)
                    }
                )
            }

            // ── Premium section ───────────────────────────────────────────────
            item {
                SectionHeader(label = stringResource(R.string.call_bg_premium_label))
            }

            val premiumItems = CallBackground.values().filter { it.isPremium }
            items(premiumItems.size) { idx ->
                val bg = premiumItems[idx]
                CallBgCard(
                    bg = bg,
                    isSelected = selected == bg,
                    isLocked = !isPremiumUser,
                    onClick = {
                        if (isPremiumUser) {
                            selected = bg
                            CallBackgroundManager.save(context, bg)
                        }
                    }
                )
            }

            // ── Reset button ──────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.call_bg_reset_default))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun CallBgCard(
    bg: CallBackground,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFF0084FF).copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF0084FF))
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail preview
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 68.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                CallBgLayer(
                    bg = bg,
                    modifier = Modifier.fillMaxSize()
                )
                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (bg.isPremium) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD700))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = callBgName(bg),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (bg.isPremium && isLocked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (isLocked) stringResource(R.string.call_bg_premium_locked)
                    else callBgDescription(bg),
                    fontSize = 12.sp,
                    color = if (isLocked) Color(0xFFFFD700).copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (bg.isPremium && !isLocked) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Анімовано", fontSize = 10.sp, color = Color(0xFF4CAF50))
                    }
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF0084FF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun callBgName(bg: CallBackground): String = when (bg) {
    CallBackground.DEFAULT       -> stringResource(R.string.call_bg_default)
    CallBackground.SUNSET        -> stringResource(R.string.call_bg_sunset)
    CallBackground.OCEAN         -> stringResource(R.string.call_bg_ocean)
    CallBackground.AURORA        -> stringResource(R.string.call_bg_aurora)
    CallBackground.STARFIELD     -> stringResource(R.string.call_bg_starfield)
    CallBackground.NEON_CITY     -> stringResource(R.string.call_bg_neon_city)
    CallBackground.GRADIENT_SHIFT -> stringResource(R.string.call_bg_gradient_shift)
    CallBackground.CRYSTAL       -> stringResource(R.string.call_bg_crystal)
}

@Composable
fun callBgDescription(bg: CallBackground): String = when (bg) {
    CallBackground.DEFAULT       -> stringResource(R.string.call_bg_default_desc)
    CallBackground.SUNSET        -> stringResource(R.string.call_bg_sunset_desc)
    CallBackground.OCEAN         -> stringResource(R.string.call_bg_ocean_desc)
    CallBackground.AURORA        -> stringResource(R.string.call_bg_aurora_desc)
    CallBackground.STARFIELD     -> stringResource(R.string.call_bg_starfield_desc)
    CallBackground.NEON_CITY     -> stringResource(R.string.call_bg_neon_city_desc)
    CallBackground.GRADIENT_SHIFT -> stringResource(R.string.call_bg_gradient_shift_desc)
    CallBackground.CRYSTAL       -> stringResource(R.string.call_bg_crystal_desc)
}
