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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.BusinessProfile
import com.worldmates.messenger.data.model.UpdateBusinessProfileRequest

private val BizDeep   = Color(0xFF0D1B2A)
private val BizDark   = Color(0xFF1A2942)
private val BizMid    = Color(0xFF243B55)
private val BizAccent = Color(0xFF1E90FF)
private val BizGold   = Color(0xFFFFD166)
private val BizCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileEditScreen(
    state:  BusinessUiState,
    onSave: (UpdateBusinessProfileRequest) -> Unit,
    onBack: () -> Unit
) {
    val profile = state.profile

    var businessName by remember(profile) { mutableStateOf(profile?.businessName ?: "") }
    var category     by remember(profile) { mutableStateOf(profile?.category ?: "") }
    var description  by remember(profile) { mutableStateOf(profile?.description ?: "") }
    var address      by remember(profile) { mutableStateOf(profile?.address ?: "") }
    var phone        by remember(profile) { mutableStateOf(profile?.phone ?: "") }
    var email        by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var website      by remember(profile) { mutableStateOf(profile?.website ?: "") }
    var badgeEnabled by remember(profile) { mutableStateOf(profile?.badgeEnabled != 0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BizDeep)
    ) {
        // Header
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
                Text(stringResource(R.string.biz_profile_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BizTextField(businessName, { businessName = it }, stringResource(R.string.biz_field_name), Icons.Default.Store)
            BizTextField(category,     { category = it },     stringResource(R.string.biz_field_category), Icons.Default.Category)
            BizTextField(description,  { description = it },  stringResource(R.string.biz_field_desc), Icons.Default.Notes, singleLine = false, minLines = 3)
            BizTextField(address,      { address = it },      stringResource(R.string.biz_field_address), Icons.Default.LocationOn)
            BizTextField(phone,        { phone = it },        stringResource(R.string.biz_field_phone), Icons.Default.Phone)
            BizTextField(email,        { email = it },        stringResource(R.string.biz_field_email), Icons.Default.Email)
            BizTextField(website,      { website = it },      stringResource(R.string.biz_field_website), Icons.Default.Language)

            // Badge toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = BizCard)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, null, tint = BizGold)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.biz_badge_title), color = Color.White, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.biz_badge_subtitle), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                    Switch(
                        checked  = badgeEnabled,
                        onCheckedChange = { badgeEnabled = it },
                        colors   = SwitchDefaults.colors(checkedThumbColor = BizAccent, checkedTrackColor = BizAccent.copy(alpha = 0.4f))
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // Save button
        Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                onClick = {
                    onSave(
                        UpdateBusinessProfileRequest(
                            businessName = businessName.ifBlank { null },
                            category     = category.ifBlank { null },
                            description  = description.ifBlank { null },
                            address      = address.ifBlank { null },
                            phone        = phone.ifBlank { null },
                            email        = email.ifBlank { null },
                            website      = website.ifBlank { null },
                            badgeEnabled = badgeEnabled,
                            autoReplyEnabled  = state.profile?.autoReplyEnabled == 1,
                            autoReplyText     = state.profile?.autoReplyText,
                            autoReplyMode     = state.profile?.autoReplyMode ?: "always",
                            greetingEnabled   = state.profile?.greetingEnabled == 1,
                            greetingText      = state.profile?.greetingText,
                            awayEnabled       = state.profile?.awayEnabled == 1,
                            awayText          = state.profile?.awayText
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = BizAccent),
                enabled  = !state.isLoading
            ) {
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BizTextField(
    value:     String,
    onValueChange: (String) -> Unit,
    label:     String,
    icon:      ImageVector,
    singleLine: Boolean = true,
    minLines:  Int = 1
) {
    OutlinedTextField(
        value          = value,
        onValueChange  = onValueChange,
        label          = { Text(label, color = Color.White.copy(alpha = 0.7f)) },
        leadingIcon    = { Icon(icon, null, tint = BizAccent) },
        singleLine     = singleLine,
        minLines       = minLines,
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
