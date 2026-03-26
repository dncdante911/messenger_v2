package com.worldmates.messenger.ui.business

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.UpdateBusinessProfileRequest

private val BizDeep   = Color(0xFF0D1B2A)
private val BizDark   = Color(0xFF1A2942)
private val BizMid    = Color(0xFF243B55)
private val BizAccent = Color(0xFF1E90FF)
private val BizCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReplyScreen(
    state:  BusinessUiState,
    onSave: (UpdateBusinessProfileRequest) -> Unit,
    onBack: () -> Unit
) {
    val p = state.profile
    var enabled   by remember(p) { mutableStateOf(p?.autoReplyEnabled == 1) }
    var text      by remember(p) { mutableStateOf(p?.autoReplyText ?: "") }
    var mode      by remember(p) { mutableStateOf(p?.autoReplyMode ?: "always") }
    var awayEnabled by remember(p) { mutableStateOf(p?.awayEnabled == 1) }
    var awayText  by remember(p) { mutableStateOf(p?.awayText ?: "") }

    Column(modifier = Modifier.fillMaxSize().background(BizDeep)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(BizMid, BizDark)))
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.biz_auto_reply_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Enable auto-reply
            BizToggleCard(
                title    = stringResource(R.string.biz_auto_reply_enable),
                subtitle = stringResource(R.string.biz_auto_reply_desc),
                icon     = Icons.Default.ReplyAll,
                checked  = enabled,
                onToggle = { enabled = it }
            )

            if (enabled) {
                // Mode selector
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = BizCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.biz_auto_reply_mode_title), color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        BizRadioOption(stringResource(R.string.biz_mode_always),         "always",         mode) { mode = it }
                        BizRadioOption(stringResource(R.string.biz_mode_outside_hours),  "outside_hours",  mode) { mode = it }
                    }
                }

                OutlinedTextField(
                    value          = text,
                    onValueChange  = { text = it },
                    label          = { Text(stringResource(R.string.biz_auto_reply_text_hint), color = Color.White.copy(alpha = 0.7f)) },
                    leadingIcon    = { Icon(Icons.Default.Message, null, tint = BizAccent) },
                    singleLine     = false,
                    minLines       = 4,
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(12.dp),
                    colors         = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BizAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = BizAccent
                    )
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Away message
            BizToggleCard(
                title    = stringResource(R.string.biz_away_enable),
                subtitle = stringResource(R.string.biz_away_desc),
                icon     = Icons.Default.BeachAccess,
                checked  = awayEnabled,
                onToggle = { awayEnabled = it }
            )
            if (awayEnabled) {
                OutlinedTextField(
                    value          = awayText,
                    onValueChange  = { awayText = it },
                    label          = { Text(stringResource(R.string.biz_away_text_hint), color = Color.White.copy(alpha = 0.7f)) },
                    leadingIcon    = { Icon(Icons.Default.NightShelter, null, tint = BizAccent) },
                    singleLine     = false,
                    minLines       = 3,
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(12.dp),
                    colors         = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BizAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = BizAccent
                    )
                )
            }

            Spacer(Modifier.height(80.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                onClick = {
                    onSave(buildProfileRequest(
                        existing     = state.profile,
                        autoEnabled  = enabled,
                        autoText     = text.ifBlank { null },
                        autoMode     = mode,
                        awayEnabled  = awayEnabled,
                        awayText     = awayText.ifBlank { null }
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = BizAccent),
                enabled  = !state.isLoading
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Greeting screen ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GreetingScreen(
    state:  BusinessUiState,
    onSave: (UpdateBusinessProfileRequest) -> Unit,
    onBack: () -> Unit
) {
    val p = state.profile
    var enabled by remember(p) { mutableStateOf(p?.greetingEnabled == 1) }
    var text    by remember(p) { mutableStateOf(p?.greetingText ?: "") }

    Column(modifier = Modifier.fillMaxSize().background(BizDeep)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(BizMid, BizDark)))
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.biz_greeting_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BizToggleCard(
                title    = stringResource(R.string.biz_greeting_enable),
                subtitle = stringResource(R.string.biz_greeting_desc),
                icon     = Icons.Default.EmojiPeople,
                checked  = enabled,
                onToggle = { enabled = it }
            )

            if (enabled) {
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    label         = { Text(stringResource(R.string.biz_greeting_text_hint), color = Color.White.copy(alpha = 0.7f)) },
                    leadingIcon   = { Icon(Icons.Default.EmojiEmotions, null, tint = BizAccent) },
                    singleLine    = false,
                    minLines      = 4,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BizAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = BizAccent
                    )
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = BizCard)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, null, tint = BizAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.biz_greeting_info),
                            color   = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                onClick = {
                    onSave(buildProfileRequest(
                        existing        = state.profile,
                        greetingEnabled = enabled,
                        greetingText    = text.ifBlank { null }
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = BizAccent),
                enabled  = !state.isLoading
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
fun BizToggleCard(
    title:    String,
    subtitle: String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit
) {
    val BizCard   = Color(0xFF233044)
    val BizAccent = Color(0xFF1E90FF)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = BizCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = BizAccent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
            Switch(
                checked         = checked,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor  = BizAccent,
                    checkedTrackColor  = BizAccent.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
fun BizRadioOption(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected == value,
            onClick  = { onSelect(value) },
            colors   = RadioButtonDefaults.colors(selectedColor = Color(0xFF1E90FF))
        )
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    }
}

fun buildProfileRequest(
    existing:        com.worldmates.messenger.data.model.BusinessProfile?,
    autoEnabled:     Boolean  = existing?.autoReplyEnabled == 1,
    autoText:        String?  = existing?.autoReplyText,
    autoMode:        String   = existing?.autoReplyMode ?: "always",
    awayEnabled:     Boolean  = existing?.awayEnabled == 1,
    awayText:        String?  = existing?.awayText,
    greetingEnabled: Boolean  = existing?.greetingEnabled == 1,
    greetingText:    String?  = existing?.greetingText
): UpdateBusinessProfileRequest = UpdateBusinessProfileRequest(
    businessName     = existing?.businessName,
    category         = existing?.category,
    description      = existing?.description,
    address          = existing?.address,
    lat              = existing?.lat,
    lng              = existing?.lng,
    phone            = existing?.phone,
    email            = existing?.email,
    website          = existing?.website,
    badgeEnabled     = existing?.badgeEnabled != 0,
    autoReplyEnabled = autoEnabled,
    autoReplyText    = autoText,
    autoReplyMode    = autoMode,
    awayEnabled      = awayEnabled,
    awayText         = awayText,
    greetingEnabled  = greetingEnabled,
    greetingText     = greetingText
)
