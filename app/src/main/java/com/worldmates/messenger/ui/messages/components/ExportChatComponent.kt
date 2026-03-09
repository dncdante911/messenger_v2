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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File

/**
 * Bottom sheet / dialog that lets the user choose export format
 * (JSON or HTML) and triggers the download + share flow.
 *
 * @param onExport  Called with format ("json"/"html"). Should return the ResponseBody.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportChatBottomSheet(
    chatName: String,
    isGroup: Boolean,
    onDismiss: () -> Unit,
    onExport: suspend (format: String) -> ResponseBody?
) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
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
                    text = error!!,
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
                                val body = onExport("json")
                                if (body != null) {
                                    saveAndShare(context, body.bytes(), "chat_export.json", "application/json")
                                    onDismiss()
                                } else {
                                    error = context.getString(R.string.export_error)
                                }
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
                                val body = onExport("html")
                                if (body != null) {
                                    saveAndShare(context, body.bytes(), "chat_export.html", "text/html")
                                    onDismiss()
                                } else {
                                    error = context.getString(R.string.export_error)
                                }
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

private suspend fun saveAndShare(context: Context, bytes: ByteArray, fileName: String, mimeType: String) {
    withContext(Dispatchers.IO) {
        val cacheDir  = File(context.cacheDir, "exports").also { it.mkdirs() }
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
