package com.worldmates.messenger.ui.channels

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.worldmates.messenger.R
import com.worldmates.messenger.network.ChannelBackupResponse
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Card shown in the channel admin settings to trigger a full JSON export of all posts.
 * The downloaded file is saved to the device's Downloads folder.
 */
@Composable
fun ChannelBackupCard(
    channelId: Long,
    channelName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var isExporting by remember { mutableStateOf(false) }
    var result      by remember { mutableStateOf<ChannelBackupResponse?>(null) }
    var error       by remember { mutableStateOf<String?>(null) }

    Card(
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text       = stringResource(R.string.channel_backup_title),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = stringResource(R.string.channel_backup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Result info
            if (result != null) {
                Surface(
                    shape  = RoundedCornerShape(10.dp),
                    color  = Color(0xFF4CAF50).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint               = Color(0xFF4CAF50),
                            modifier           = Modifier.size(20.dp)
                        )
                        Text(
                            text  = stringResource(R.string.channel_backup_success, result!!.postsCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF388E3C)
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    text  = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Export button
            Button(
                onClick = {
                    error = null
                    result = null
                    scope.launch {
                        isExporting = true
                        try {
                            val resp = withContext(Dispatchers.IO) {
                                NodeRetrofitClient.channelApi.exportChannel(channelId)
                            }
                            if (resp.apiStatus == 200) {
                                withContext(Dispatchers.IO) {
                                    saveBackupToDownloads(context, resp, channelName)
                                }
                                result = resp
                            } else {
                                error = resp.errorMessage ?: "Export failed"
                            }
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Unknown error"
                        } finally {
                            isExporting = false
                        }
                    }
                },
                enabled  = !isExporting,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.channel_backup_exporting))
                } else {
                    Icon(
                        imageVector        = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.channel_backup_export), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Save to Downloads ────────────────────────────────────────────────────────

private fun saveBackupToDownloads(
    context: Context,
    backup: ChannelBackupResponse,
    channelName: String
) {
    val json     = Gson().toJson(backup)
    val fileName = "channel_${backup.channelId}_backup_${backup.exportedAt}.json"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri      = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> out.write(json.toByteArray()) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } else {
        @Suppress("DEPRECATION")
        val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = java.io.File(dir, fileName)
        file.writeText(json)
    }
}
