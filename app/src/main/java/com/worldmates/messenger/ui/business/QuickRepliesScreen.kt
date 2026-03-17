package com.worldmates.messenger.ui.business

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.BusinessQuickReply

private val BizDeep   = Color(0xFF0D1B2A)
private val BizDark   = Color(0xFF1A2942)
private val BizMid    = Color(0xFF243B55)
private val BizAccent = Color(0xFF1E90FF)
private val BizGold   = Color(0xFFFFD166)
private val BizCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRepliesScreen(
    state:     BusinessUiState,
    onCreate:  (String, String) -> Unit,
    onUpdate:  (Long, String, String) -> Unit,
    onDelete:  (Long) -> Unit,
    onBack:    () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editItem   by remember { mutableStateOf<BusinessQuickReply?>(null) }

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
                Text(stringResource(R.string.biz_quick_replies_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { editItem = null; showDialog = true }) {
                    Icon(Icons.Default.Add, null, tint = BizGold)
                }
            }
        }

        if (state.quickReplies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Quickreply, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.biz_no_quick_replies), color = Color.White.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showDialog = true },
                        colors  = ButtonDefaults.buttonColors(containerColor = BizAccent)
                    ) {
                        Text(stringResource(R.string.biz_add_quick_reply))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.quickReplies, key = { it.id }) { qr ->
                    QuickReplyItem(
                        item     = qr,
                        onEdit   = { editItem = it; showDialog = true },
                        onDelete = onDelete
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showDialog) {
        QuickReplyDialog(
            initial  = editItem,
            onDismiss = { showDialog = false; editItem = null },
            onSave   = { shortcut, text ->
                val e = editItem
                if (e != null) onUpdate(e.id, shortcut, text)
                else           onCreate(shortcut, text)
                showDialog = false
                editItem   = null
            }
        )
    }
}

@Composable
private fun QuickReplyItem(
    item:     BusinessQuickReply,
    onEdit:   (BusinessQuickReply) -> Unit,
    onDelete: (Long) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = BizCard)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("/${item.shortcut}", color = BizGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(item.text, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onEdit(item) }) {
                Icon(Icons.Default.Edit, null, tint = BizAccent)
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B))
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title            = { Text(stringResource(R.string.biz_delete_confirm_title)) },
            text             = { Text(stringResource(R.string.biz_delete_qr_message, item.shortcut)) },
            confirmButton    = {
                TextButton(onClick = { onDelete(item.id); showConfirm = false }) {
                    Text(stringResource(R.string.delete), color = Color(0xFFFF6B6B))
                }
            },
            dismissButton    = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplyDialog(
    initial:  BusinessQuickReply?,
    onDismiss: () -> Unit,
    onSave:   (String, String) -> Unit
) {
    var shortcut by remember { mutableStateOf(initial?.shortcut ?: "") }
    var text     by remember { mutableStateOf(initial?.text ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = {
            Text(
                if (initial != null) stringResource(R.string.biz_edit_quick_reply)
                else                 stringResource(R.string.biz_add_quick_reply)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = shortcut,
                    onValueChange = { shortcut = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                    label         = { Text(stringResource(R.string.biz_qr_shortcut_hint)) },
                    leadingIcon   = { Text("/", fontWeight = FontWeight.Bold) },
                    singleLine    = true
                )
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    label         = { Text(stringResource(R.string.biz_qr_text_hint)) },
                    singleLine    = false,
                    minLines      = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (shortcut.isNotBlank() && text.isNotBlank()) onSave(shortcut, text) },
                enabled  = shortcut.isNotBlank() && text.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
