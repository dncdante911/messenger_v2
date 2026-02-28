package com.worldmates.messenger.ui.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.components.formatting.FormattingSettings

/**
 * Admin panel for managing text formatting settings in groups/channels
 *
 * Allows admins to:
 * - Enable/disable specific formatting features
 * - Set restrictions for members vs admins
 * - Preview how formatting looks
 */

// ==================== SETTINGS STATE ====================

data class GroupFormattingPermissions(
    val membersCanUseMentions: Boolean = true,
    val membersCanUseHashtags: Boolean = true,
    val membersCanUseBold: Boolean = true,
    val membersCanUseItalic: Boolean = true,
    val membersCanUseCode: Boolean = true,
    val membersCanUseStrikethrough: Boolean = true,
    val membersCanUseUnderline: Boolean = true,
    val membersCanUseSpoilers: Boolean = true,
    val membersCanUseQuotes: Boolean = true,
    val membersCanUseLinks: Boolean = true,
    // Admins always can use all features
    val adminsOnly: Set<String> = emptySet() // Features restricted to admins only
)

fun GroupFormattingPermissions.toFormattingSettings(isAdmin: Boolean): FormattingSettings {
    return FormattingSettings(
        allowMentions = isAdmin || membersCanUseMentions,
        allowHashtags = isAdmin || membersCanUseHashtags,
        allowBold = isAdmin || membersCanUseBold,
        allowItalic = isAdmin || membersCanUseItalic,
        allowCode = isAdmin || membersCanUseCode,
        allowStrikethrough = isAdmin || membersCanUseStrikethrough,
        allowUnderline = isAdmin || membersCanUseUnderline,
        allowSpoilers = isAdmin || membersCanUseSpoilers,
        allowQuotes = isAdmin || membersCanUseQuotes,
        allowLinks = isAdmin || membersCanUseLinks
    )
}

// ==================== MAIN PANEL ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormattingSettingsPanel(
    currentSettings: GroupFormattingPermissions,
    isChannel: Boolean = false,
    onSettingsChange: (GroupFormattingPermissions) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localSettings by remember(currentSettings) { mutableStateOf(currentSettings) }
    var showAdvanced by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = stringResource(R.string.formatting_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (isChannel) {
                    stringResource(R.string.formatting_subtitle_channel)
                } else {
                    stringResource(R.string.formatting_subtitle_group)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Quick presets
            Text(
                text = stringResource(R.string.quick_settings),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetButton(
                    label = stringResource(R.string.all_enabled),
                    selected = isAllEnabled(localSettings),
                    onClick = { localSettings = enableAll() },
                    modifier = Modifier.weight(1f)
                )
                PresetButton(
                    label = stringResource(R.string.basic),
                    selected = isBasic(localSettings),
                    onClick = { localSettings = basicSettings() },
                    modifier = Modifier.weight(1f)
                )
                PresetButton(
                    label = stringResource(R.string.text_only),
                    selected = isTextOnly(localSettings),
                    onClick = { localSettings = textOnly() },
                    modifier = Modifier.weight(1f)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Basic formatting section
            SettingsSection(title = stringResource(R.string.basic_formatting)) {
                FormattingToggle(
                    icon = Icons.Default.FormatBold,
                    title = stringResource(R.string.bold_text),
                    description = stringResource(R.string.bold_syntax),
                    enabled = localSettings.membersCanUseBold,
                    onToggle = { localSettings = localSettings.copy(membersCanUseBold = it) }
                )

                FormattingToggle(
                    icon = Icons.Default.FormatItalic,
                    title = stringResource(R.string.italic_text),
                    description = stringResource(R.string.italic_syntax),
                    enabled = localSettings.membersCanUseItalic,
                    onToggle = { localSettings = localSettings.copy(membersCanUseItalic = it) }
                )

                FormattingToggle(
                    icon = Icons.Default.FormatStrikethrough,
                    title = stringResource(R.string.strikethrough_text),
                    description = stringResource(R.string.strikethrough_syntax),
                    enabled = localSettings.membersCanUseStrikethrough,
                    onToggle = { localSettings = localSettings.copy(membersCanUseStrikethrough = it) }
                )

                FormattingToggle(
                    icon = Icons.Default.FormatUnderlined,
                    title = stringResource(R.string.underline_text),
                    description = stringResource(R.string.underline_syntax),
                    enabled = localSettings.membersCanUseUnderline,
                    onToggle = { localSettings = localSettings.copy(membersCanUseUnderline = it) }
                )
            }

            // Code and technical
            SettingsSection(title = stringResource(R.string.technical_code)) {
                FormattingToggle(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.code),
                    description = stringResource(R.string.code_syntax),
                    enabled = localSettings.membersCanUseCode,
                    onToggle = { localSettings = localSettings.copy(membersCanUseCode = it) }
                )

                FormattingToggle(
                    icon = Icons.Default.FormatQuote,
                    title = stringResource(R.string.quotes),
                    description = stringResource(R.string.quote_syntax),
                    enabled = localSettings.membersCanUseQuotes,
                    onToggle = { localSettings = localSettings.copy(membersCanUseQuotes = it) }
                )
            }

            // Social features
            SettingsSection(title = stringResource(R.string.social_functions)) {
                FormattingToggle(
                    icon = Icons.Default.AlternateEmail,
                    title = stringResource(R.string.mentions),
                    description = stringResource(R.string.mention_syntax),
                    enabled = localSettings.membersCanUseMentions,
                    onToggle = { localSettings = localSettings.copy(membersCanUseMentions = it) }
                )

                FormattingToggle(
                    icon = Icons.Default.Tag,
                    title = stringResource(R.string.hashtags),
                    description = stringResource(R.string.hashtag_syntax),
                    enabled = localSettings.membersCanUseHashtags,
                    onToggle = { localSettings = localSettings.copy(membersCanUseHashtags = it) }
                )

                FormattingToggle(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.links),
                    description = stringResource(R.string.link_syntax),
                    enabled = localSettings.membersCanUseLinks,
                    onToggle = { localSettings = localSettings.copy(membersCanUseLinks = it) }
                )
            }

            // Special features
            SettingsSection(title = stringResource(R.string.special_functions)) {
                FormattingToggle(
                    icon = Icons.Default.VisibilityOff,
                    title = stringResource(R.string.spoilers),
                    description = stringResource(R.string.spoiler_syntax),
                    enabled = localSettings.membersCanUseSpoilers,
                    onToggle = { localSettings = localSettings.copy(membersCanUseSpoilers = it) },
                    highlightColor = Color(0xFF6B5B95)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick = {
                        onSettingsChange(localSettings)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

// ==================== COMPONENTS ====================

@Composable
private fun FormattingToggle(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    highlightColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!enabled) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = highlightColor,
                checkedTrackColor = highlightColor.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun PresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

// ==================== PRESET HELPERS ====================

private fun enableAll() = GroupFormattingPermissions(
    membersCanUseMentions = true,
    membersCanUseHashtags = true,
    membersCanUseBold = true,
    membersCanUseItalic = true,
    membersCanUseCode = true,
    membersCanUseStrikethrough = true,
    membersCanUseUnderline = true,
    membersCanUseSpoilers = true,
    membersCanUseQuotes = true,
    membersCanUseLinks = true
)

private fun basicSettings() = GroupFormattingPermissions(
    membersCanUseMentions = true,
    membersCanUseHashtags = true,
    membersCanUseBold = true,
    membersCanUseItalic = true,
    membersCanUseCode = false,
    membersCanUseStrikethrough = false,
    membersCanUseUnderline = false,
    membersCanUseSpoilers = false,
    membersCanUseQuotes = false,
    membersCanUseLinks = true
)

private fun textOnly() = GroupFormattingPermissions(
    membersCanUseMentions = false,
    membersCanUseHashtags = false,
    membersCanUseBold = false,
    membersCanUseItalic = false,
    membersCanUseCode = false,
    membersCanUseStrikethrough = false,
    membersCanUseUnderline = false,
    membersCanUseSpoilers = false,
    membersCanUseQuotes = false,
    membersCanUseLinks = false
)

private fun isAllEnabled(settings: GroupFormattingPermissions): Boolean {
    return settings == enableAll()
}

private fun isBasic(settings: GroupFormattingPermissions): Boolean {
    return settings == basicSettings()
}

private fun isTextOnly(settings: GroupFormattingPermissions): Boolean {
    return settings == textOnly()
}

// ==================== COMPACT SETTINGS BUTTON ====================

/**
 * Button for opening formatting settings (for use in admin panel)
 */
@Composable
fun FormattingSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TextFormat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.text_formatting),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.configure_allowed_features),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
