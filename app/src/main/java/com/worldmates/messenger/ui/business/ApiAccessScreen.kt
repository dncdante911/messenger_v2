package com.worldmates.messenger.ui.business

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

private val BizDeep   = Color(0xFF0D1B2A)
private val BizAccent = Color(0xFF1E90FF)
private val BizGold   = Color(0xFFFFD166)
private val BizCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiAccessScreen(
    state:       BusinessUiState,
    onBack:      () -> Unit,
    onGenerate:  () -> Unit,
    onRevoke:    () -> Unit,
    onLoadKey:   () -> Unit,
) {
    LaunchedEffect(Unit) { onLoadKey() }

    val context = LocalContext.current
    var showRevokeDialog by remember { mutableStateOf(false) }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(stringResource(R.string.biz_api_revoke_title)) },
            text  = { Text(stringResource(R.string.biz_api_revoke_confirm)) },
            confirmButton = {
                TextButton(onClick = { showRevokeDialog = false; onRevoke() }) {
                    Text(stringResource(R.string.biz_api_revoke_btn), color = Color(0xFFE74C3C))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(stringResource(R.string.biz_api_cancel))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BizDeep)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.biz_api_title), color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A2942)),
                )
            },
        ) { padding ->
            Column(
                modifier            = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Info card
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BizCard),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.biz_api_what_is), color = BizGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(stringResource(R.string.biz_api_desc), color = Color.White.copy(0.8f), fontSize = 13.sp)

                        Divider(color = Color.White.copy(0.1f))

                        Text(stringResource(R.string.biz_api_usage_title), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "GET /api/node/business/export\nAuthorization: Bearer <api_key>",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp,
                            color      = BizAccent.copy(0.9f),
                        )
                    }
                }

                if (state.isApiKeyLoading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BizAccent)
                    }
                } else {
                    val key = state.apiKey
                    if (key != null) {
                        // Key display card
                        Card(
                            shape  = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BizCard),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Key, null, tint = BizGold, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.biz_api_your_key), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                SelectionContainer {
                                    Text(
                                        text       = key.apiKey,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize   = 12.sp,
                                        color      = Color(0xFF8BE9FD),
                                    )
                                }
                                key.lastUsedAt?.let {
                                    Text(
                                        stringResource(R.string.biz_api_last_used, it),
                                        color    = Color.White.copy(0.5f),
                                        fontSize = 11.sp,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { copyToClipboard(context, key.apiKey) },
                                        modifier = Modifier.weight(1f),
                                        shape    = RoundedCornerShape(10.dp),
                                    ) {
                                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.biz_api_copy))
                                    }
                                    OutlinedButton(
                                        onClick = onGenerate,
                                        modifier = Modifier.weight(1f),
                                        shape    = RoundedCornerShape(10.dp),
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.biz_api_regenerate))
                                    }
                                }
                                TextButton(
                                    onClick = { showRevokeDialog = true },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFE74C3C), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.biz_api_revoke_btn), color = Color(0xFFE74C3C), fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        // No key yet
                        Card(
                            shape  = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BizCard),
                        ) {
                            Column(
                                modifier              = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment   = Alignment.CenterHorizontally,
                                verticalArrangement   = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Default.Key, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(48.dp))
                                Text(
                                    stringResource(R.string.biz_api_no_key),
                                    color = Color.White.copy(0.6f),
                                )
                                Button(
                                    onClick = onGenerate,
                                    shape   = RoundedCornerShape(12.dp),
                                    colors  = ButtonDefaults.buttonColors(containerColor = BizAccent),
                                ) {
                                    Icon(Icons.Default.Add, null, tint = Color.White)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.biz_api_generate_btn), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("API Key", text))
}
