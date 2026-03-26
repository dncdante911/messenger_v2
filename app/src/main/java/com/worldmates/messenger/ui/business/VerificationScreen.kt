package com.worldmates.messenger.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

private val BizDeep   = Color(0xFF0D1B2A)
private val BizAccent = Color(0xFF1E90FF)
private val BizGold   = Color(0xFFFFD166)
private val BizCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    state:    BusinessUiState,
    onBack:   () -> Unit,
    onRequest: () -> Unit,
) {
    val verStatus = state.profile?.verificationStatus ?: "none"

    Box(modifier = Modifier.fillMaxSize().background(BizDeep)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.biz_verified_title), color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A2942)),
                )
            },
        ) { padding ->
            Column(
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(16.dp))

                // Status badge
                StatusBadge(verStatus)

                // Description
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BizCard),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.biz_verified_what_gives),
                            color = BizGold, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        )
                        BenefitRow(icon = "✅", text = stringResource(R.string.biz_verified_benefit_badge))
                        BenefitRow(icon = "🔍", text = stringResource(R.string.biz_verified_benefit_search))
                        BenefitRow(icon = "📊", text = stringResource(R.string.biz_verified_benefit_stats))
                        BenefitRow(icon = "💬", text = stringResource(R.string.biz_verified_benefit_trust))
                    }
                }

                // Requirements
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BizCard),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.biz_verified_requirements),
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        )
                        RequirementRow(
                            met  = state.profile?.businessName != null,
                            text = stringResource(R.string.biz_verified_req_name),
                        )
                        RequirementRow(
                            met  = state.profile?.description != null,
                            text = stringResource(R.string.biz_verified_req_desc),
                        )
                        RequirementRow(
                            met  = state.profile?.phone != null || state.profile?.email != null,
                            text = stringResource(R.string.biz_verified_req_contacts),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                when (verStatus) {
                    "approved" -> {
                        Button(
                            onClick  = {},
                            enabled  = false,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.biz_verified_status_approved), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    "pending" -> {
                        Button(
                            onClick  = {},
                            enabled  = false,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = BizAccent.copy(0.5f)),
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.biz_verified_status_pending), color = Color.White)
                        }
                    }
                    else -> {
                        Button(
                            onClick  = onRequest,
                            enabled  = !state.isLoading && state.profile != null,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = BizAccent),
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Verified, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.biz_verified_request_btn), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (icon, color, label) = when (status) {
        "approved" -> Triple(Icons.Default.CheckCircle, Color(0xFF2ECC71), R.string.biz_verified_status_approved)
        "pending"  -> Triple(Icons.Default.HourglassTop, Color(0xFFFFD166), R.string.biz_verified_status_pending)
        "rejected" -> Triple(Icons.Default.Cancel, Color(0xFFE74C3C), R.string.biz_verified_status_rejected)
        else       -> Triple(Icons.Default.Shield, Color(0xFF5D6E80), R.string.biz_verified_status_none)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier         = Modifier.size(80.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(label), color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun BenefitRow(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White.copy(0.85f), fontSize = 13.sp)
    }
}

@Composable
private fun RequirementRow(met: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint   = if (met) Color(0xFF2ECC71) else Color.White.copy(0.4f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text     = text,
            color    = if (met) Color.White else Color.White.copy(0.5f),
            fontSize = 13.sp,
        )
    }
}
