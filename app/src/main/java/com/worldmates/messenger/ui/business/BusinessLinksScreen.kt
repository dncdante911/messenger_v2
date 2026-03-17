package com.worldmates.messenger.ui.business

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.BusinessLink

private val BizDeep   = Color(0xFF0D1B2A)
private val BizDark   = Color(0xFF1A2942)
private val BizMid    = Color(0xFF243B55)
private val BizAccent = Color(0xFF1E90FF)
private val BizGold   = Color(0xFFFFD166)
private val BizCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessLinksScreen(
    state:     BusinessUiState,
    onCreate:  (String, String?) -> Unit,
    onDelete:  (Long) -> Unit,
    onBack:    () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

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
                Text(stringResource(R.string.biz_links_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, null, tint = BizGold)
                }
            }
        }

        if (state.links.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Link, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.biz_no_links), color = Color.White.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showDialog = true },
                        colors  = ButtonDefaults.buttonColors(containerColor = BizAccent)
                    ) { Text(stringResource(R.string.biz_create_link)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.links, key = { it.id }) { link ->
                    BusinessLinkItem(link = link, onDelete = onDelete)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showDialog) {
        CreateLinkDialog(
            onDismiss = { showDialog = false },
            onSave    = { title, prefilled ->
                onCreate(title, prefilled)
                showDialog = false
            }
        )
    }
}

@Composable
private fun BusinessLinkItem(link: BusinessLink, onDelete: (Long) -> Unit) {
    val context       = LocalContext.current
    var showConfirm   by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = BizCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, null, tint = BizAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(link.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("${link.views} ${stringResource(R.string.biz_link_views)}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            if (!link.url.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(link.url, color = BizAccent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!link.prefilledText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.biz_link_prefilled, link.prefilledText),
                    color    = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("link", link.url))
                        Toast.makeText(context, context.getString(R.string.biz_link_copied), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = BizGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.biz_copy_link), color = BizGold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showConfirm = true }) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.delete), color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.biz_delete_confirm_title)) },
            text  = { Text(stringResource(R.string.biz_delete_link_message, link.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(link.id); showConfirm = false }) {
                    Text(stringResource(R.string.delete), color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLinkDialog(
    onDismiss: () -> Unit,
    onSave:    (String, String?) -> Unit
) {
    var title     by remember { mutableStateOf("") }
    var prefilled by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.biz_create_link)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text(stringResource(R.string.biz_link_title_hint)) },
                    singleLine    = true
                )
                OutlinedTextField(
                    value         = prefilled,
                    onValueChange = { prefilled = it },
                    label         = { Text(stringResource(R.string.biz_link_prefilled_hint)) },
                    singleLine    = false,
                    minLines      = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (title.isNotBlank()) onSave(title, prefilled.ifBlank { null }) },
                enabled  = title.isNotBlank()
            ) { Text(stringResource(R.string.biz_create_link)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
