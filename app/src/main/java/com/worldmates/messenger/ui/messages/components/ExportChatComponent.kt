package com.worldmates.messenger.ui.messages.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bottom sheet that lets the user export the current (already-decrypted) message list.
 * Builds JSON or HTML locally — no server call needed, so E2EE messages are included.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportChatBottomSheet(
    chatName: String,
    isGroup: Boolean,
    messages: List<Message>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.export_chat_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.export_chat_subtitle, chatName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // JSON export
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            try {
                                val bytes = buildJson(messages).toByteArray(Charsets.UTF_8)
                                saveAndShare(context, bytes, "chat_export.json", "application/json")
                                onDismiss()
                            } catch (e: Exception) {
                                error = e.message ?: context.getString(R.string.export_error)
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.export_format_json))
                }

                // HTML export
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            try {
                                val bytes = buildHtml(chatName, messages).toByteArray(Charsets.UTF_8)
                                saveAndShare(context, bytes, "chat_export.html", "text/html")
                                onDismiss()
                            } catch (e: Exception) {
                                error = e.message ?: context.getString(R.string.export_error)
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.export_format_html))
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.export_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ── Builders ──────────────────────────────────────────────────────────────────

private fun buildJson(messages: List<Message>): String {
    val sb = StringBuilder("[\n")
    messages.forEachIndexed { i, m ->
        val text  = (m.decryptedText ?: "").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val name  = (m.senderName ?: "").replace("\"", "\\\"")
        val media = (m.mediaUrl ?: "").replace("\"", "\\\"")
        sb.append("  {")
        sb.append("\"id\":${m.id},")
        sb.append("\"from_id\":${m.fromId},")
        sb.append("\"sender\":\"$name\",")
        sb.append("\"time\":${m.timeStamp},")
        sb.append("\"text\":\"$text\",")
        sb.append("\"media\":\"$media\"")
        sb.append("}")
        if (i < messages.size - 1) sb.append(",")
        sb.append("\n")
    }
    sb.append("]")
    return sb.toString()
}

private fun buildHtml(chatName: String, messages: List<Message>): String {
    val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    val sb = StringBuilder("""
<!DOCTYPE html><html><head><meta charset="UTF-8">
<title>${chatName.htmlEscape()}</title>
<style>
  body{font-family:sans-serif;max-width:800px;margin:0 auto;padding:16px;background:#f5f5f5}
  h1{font-size:18px;color:#333}
  .msg{background:#fff;border-radius:8px;padding:8px 12px;margin:6px 0;box-shadow:0 1px 2px rgba(0,0,0,.1)}
  .sender{font-weight:bold;font-size:12px;color:#5c6bc0}
  .time{font-size:11px;color:#aaa;float:right}
  .text{margin-top:4px;font-size:14px;white-space:pre-wrap}
  .media{font-size:12px;color:#1976d2;margin-top:4px}
</style></head><body>
<h1>${chatName.htmlEscape()}</h1>
""".trimIndent())
    messages.forEach { m ->
        val time   = fmt.format(Date(m.timeStamp * 1000))
        val sender = (m.senderName ?: "User ${m.fromId}").htmlEscape()
        val text   = (m.decryptedText ?: "").htmlEscape()
        val media  = m.mediaUrl ?: ""
        sb.append("<div class=\"msg\">")
        sb.append("<span class=\"sender\">$sender</span>")
        sb.append("<span class=\"time\">$time</span>")
        if (text.isNotEmpty()) sb.append("<div class=\"text\">$text</div>")
        if (media.isNotEmpty()) sb.append("<div class=\"media\"><a href=\"$media\">$media</a></div>")
        sb.append("</div>\n")
    }
    sb.append("</body></html>")
    return sb.toString()
}

private fun String.htmlEscape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private suspend fun saveAndShare(context: Context, bytes: ByteArray, fileName: String, mimeType: String) {
    withContext(Dispatchers.IO) {
        val cacheDir   = File(context.cacheDir, "exports").also { it.mkdirs() }
        val exportFile = File(cacheDir, fileName)
        exportFile.writeBytes(bytes)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile
        )

        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, null))
        }
    }
}
