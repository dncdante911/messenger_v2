package com.worldmates.messenger.ui.settings

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.BuildConfig
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.VersionChangelog
import com.worldmates.messenger.update.AppUpdateManager
import com.worldmates.messenger.update.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val updateState by AppUpdateManager.state.collectAsState()

    // Check for updates when screen opens
    LaunchedEffect(Unit) {
        viewModel.checkUpdates(force = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Version info card
            item {
                UpdateVersionCard(
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestVersion = updateState.latestVersion,
                    hasUpdate = updateState.hasUpdate,
                    isMandatory = updateState.isMandatory,
                    error = updateState.error,
                    publishedAt = updateState.publishedAt,
                    isChecking = updateState.checkedAtMillis == null,
                    downloadState = updateState.downloadState,
                    downloadProgress = updateState.downloadProgress,
                    onCheckNow = { viewModel.checkUpdates(force = true) },
                    onDownload = { AppUpdateManager.downloadAndInstall(context) },
                    onInstall = { AppUpdateManager.installPendingUpdate(context) },
                    onOpenUrl = { AppUpdateManager.openUpdateUrl(context) }
                )
            }

            // Changelog sections (structured)
            val structured = updateState.changelogStructured
            if (structured.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.changelog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(structured) { versionLog ->
                    VersionChangelogCard(versionLog)
                }
            } else if (updateState.changelog.isNotEmpty()) {
                // Fallback: flat changelog list
                item {
                    Text(
                        text = stringResource(R.string.changelog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                item {
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            updateState.changelog.forEach { entry ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                                    Text(entry, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            } else if (updateState.checkedAtMillis != null) {
                item {
                    Text(
                        text = stringResource(R.string.update_no_changelog),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun UpdateVersionCard(
    currentVersion: String,
    latestVersion: String?,
    hasUpdate: Boolean,
    isMandatory: Boolean,
    error: String?,
    publishedAt: String?,
    isChecking: Boolean,
    downloadState: DownloadState,
    downloadProgress: Int,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenUrl: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Versions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                VersionInfoColumn(
                    label = stringResource(R.string.update_current_version),
                    version = currentVersion
                )
                if (latestVersion != null) {
                    VersionInfoColumn(
                        label = stringResource(R.string.update_latest_version),
                        version = latestVersion,
                        highlight = hasUpdate
                    )
                }
            }

            HorizontalDivider()

            // Status chip
            when {
                isChecking -> StatusChip(
                    icon = Icons.Default.Refresh,
                    text = stringResource(R.string.update_status_checking),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                error != null && latestVersion == null -> StatusChip(
                    icon = Icons.Default.Warning,
                    text = stringResource(R.string.update_status_error),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                hasUpdate -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        icon = Icons.Default.NewReleases,
                        text = stringResource(R.string.update_status_available),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isMandatory) {
                        StatusChip(
                            icon = Icons.Default.PriorityHigh,
                            text = stringResource(R.string.update_mandatory_badge),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> StatusChip(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.update_status_up_to_date),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            if (publishedAt != null && hasUpdate) {
                Text(
                    text = stringResource(R.string.update_published, publishedAt.take(10)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
            when (downloadState) {
                is DownloadState.Idle -> {
                    if (hasUpdate) {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.update_download_install))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onCheckNow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.update_check_now))
                        }
                    }
                }
                is DownloadState.Downloading, is DownloadState.Progress -> {
                    val progress = if (downloadState is DownloadState.Progress) downloadState.percent else downloadProgress
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.update_downloading, progress),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is DownloadState.ReadyToInstall, is DownloadState.Downloaded -> {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.update_install))
                    }
                }
                is DownloadState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = (downloadState as DownloadState.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.update_download_install))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionInfoColumn(
    label: String,
    version: String,
    highlight: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = version,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun VersionChangelogCard(log: VersionChangelog) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Version header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "v${log.version}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!log.date.isNullOrBlank()) {
                    Text(
                        text = log.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (log.added.isNotEmpty()) {
                ChangeSection(
                    title = stringResource(R.string.changelog_section_added),
                    entries = log.added,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (log.changed.isNotEmpty()) {
                ChangeSection(
                    title = stringResource(R.string.changelog_section_changed),
                    entries = log.changed,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (log.fixed.isNotEmpty()) {
                ChangeSection(
                    title = stringResource(R.string.changelog_section_fixed),
                    entries = log.fixed,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (log.removed.isNotEmpty()) {
                ChangeSection(
                    title = stringResource(R.string.changelog_section_removed),
                    entries = log.removed,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ChangeSection(
    title: String,
    entries: List<String>,
    color: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
