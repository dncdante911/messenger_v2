package com.worldmates.messenger.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.AccountManager
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.local.entity.AccountEntity
import kotlinx.coroutines.launch

/**
 * AccountSwitcherDialog - діалог перемикання акаунтів (Telegram-style).
 */
@Composable
fun AccountSwitcherDialog(
    onDismiss: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onAddAccount: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val accounts by AccountManager.accounts.collectAsState()
    val activeAccount by AccountManager.activeAccount.collectAsState()

    val maxAccounts = AccountManager.getMaxAccounts()
    val canAdd = accounts.size < maxAccounts

    var showDeleteConfirm by remember { mutableStateOf<AccountEntity?>(null) }

    val titleText        = stringResource(R.string.multi_account_title)
    val subtypeLabel     = if (UserSession.isPro > 0)
        stringResource(R.string.multi_account_subtitle_pro)
    else
        stringResource(R.string.multi_account_subtitle_free)
    val subtitleText     = stringResource(R.string.multi_account_subtitle, accounts.size, maxAccounts) + " $subtypeLabel"
    val closeCd          = stringResource(R.string.close)
    val addAccountText   = stringResource(R.string.multi_account_add)
    val limitReachedText = stringResource(R.string.multi_account_limit_reached, maxAccounts)
    val proHintAccounts  = stringResource(R.string.premium_hint_accounts, AccountManager.MAX_ACCOUNTS_PRO)
    val deleteTitle      = stringResource(R.string.multi_account_delete_title)
    val deleteBtn        = stringResource(R.string.delete)
    val cancelBtn        = stringResource(R.string.cancel)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(0.dp)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = closeCd)
                    }
                }

                HorizontalDivider()

                // Account list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(accounts, key = { it.userId }) { account ->
                        AccountItem(
                            account = account,
                            isActive = account.userId == activeAccount?.userId,
                            onSwitch = {
                                if (account.userId != activeAccount?.userId) {
                                    scope.launch {
                                        AccountManager.switchAccount(account.userId)
                                        onSwitchAccount(account.userId)
                                    }
                                }
                                onDismiss()
                            },
                            onDelete = { showDeleteConfirm = account }
                        )
                    }
                }

                HorizontalDivider()

                // Add account button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canAdd) { onAddAccount() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canAdd)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = if (canAdd)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = addAccountText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (canAdd)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        if (!canAdd) {
                            // Якщо free-юзер — показуємо soft-hint про PRO
                            if (!UserSession.isProActive) {
                                Text(
                                    text = proHintAccounts,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB8860B)
                                )
                            } else {
                                Text(
                                    text = limitReachedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    showDeleteConfirm?.let { account ->
        val displayName = account.username ?: account.userId.toString()
        val deleteMessage = stringResource(R.string.multi_account_delete_message, displayName)

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(deleteTitle) },
            text = { Text(deleteMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { AccountManager.removeAccount(account.userId) }
                        showDeleteConfirm = null
                    }
                ) {
                    Text(deleteBtn, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(cancelBtn)
                }
            }
        )
    }
}

@Composable
private fun AccountItem(
    account: AccountEntity,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit
) {
    val activeText      = stringResource(R.string.multi_account_active)
    val tapToSwitch     = stringResource(R.string.multi_account_tap_to_switch)
    val deleteCd        = stringResource(R.string.multi_account_delete_cd)
    val fallbackName    = stringResource(R.string.multi_account_fallback_name, account.userId.toString())
    val proBadge        = stringResource(R.string.pro_badge)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSwitch)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(modifier = Modifier.size(48.dp)) {
            if (!account.avatar.isNullOrEmpty()) {
                AsyncImage(
                    model = account.avatar,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name + Pro badge
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.username ?: fallbackName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (account.isPro > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = proBadge,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
            Text(
                text = if (isActive) activeText else tapToSwitch,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        if (!isActive) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = deleteCd,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
