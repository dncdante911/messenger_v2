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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.worldmates.messenger.data.AccountManager
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.local.entity.AccountEntity
import kotlinx.coroutines.launch

/**
 * AccountSwitcherDialog - діалог перемикання акаунтів (Telegram-style).
 *
 * Показує список збережених акаунтів з можливістю:
 * - Перемкнутись на інший акаунт (одним натисканням)
 * - Видалити акаунт
 * - Додати новий акаунт
 *
 * Ліміти відображаються у підзаголовку.
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
                            text = "Акаунти",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${accounts.size} / $maxAccounts${if (UserSession.isPro > 0) " (Pro)" else " (Free)"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрити")
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
                            onDelete = {
                                showDeleteConfirm = account
                            }
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
                            text = "Додати акаунт",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (canAdd)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        if (!canAdd) {
                            Text(
                                text = "Досягнуто ліміту ($maxAccounts акаунтів)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    showDeleteConfirm?.let { account ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Видалити акаунт?") },
            text = {
                Text("Акаунт ${account.username ?: account.userId} буде видалено з цього пристрою. Дані залишаться на сервері.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            AccountManager.removeAccount(account.userId)
                        }
                        showDeleteConfirm = null
                    }
                ) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Скасувати")
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

            // Active indicator
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
                    text = account.username ?: "Акаунт ${account.userId}",
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
                            text = "PRO",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
            Text(
                text = if (isActive) "Активний" else "Натисніть для перемикання",
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        // Delete button (не для активного щоб не було випадкового видалення)
        if (!isActive) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Видалити акаунт",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
