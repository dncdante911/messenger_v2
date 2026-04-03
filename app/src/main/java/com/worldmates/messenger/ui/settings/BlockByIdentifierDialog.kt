package com.worldmates.messenger.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.R

/**
 * Діалог превентивного блокування: за номером телефону або за ID користувача.
 * Викликається з [BlockedUsersScreen] через FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockByIdentifierDialog(
    state:     BlockByIdState,
    onBlock:   (phone: String?, userId: Long?) -> Unit,
    onDismiss: () -> Unit,
    onReset:   () -> Unit,
) {
    // Tab: 0 = phone, 1 = id
    var selectedTab by remember { mutableIntStateOf(0) }
    var phoneInput  by remember { mutableStateOf("") }
    var idInput     by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    // Phone basic regex — allows +, digits, spaces, dashes, parens
    val phoneValid = phoneInput.replace(Regex("[\\s\\-()]"), "").matches(Regex("^\\+?\\d{7,15}$"))
    val idValid    = idInput.toLongOrNull()?.let { it > 0 } ?: false

    val inputValid = if (selectedTab == 0) phoneValid else idValid

    // Reset inputs when dialog reopens
    LaunchedEffect(Unit) { onReset() }

    // Auto-close on success / already_blocked after brief delay
    LaunchedEffect(state) {
        if (state == BlockByIdState.SUCCESS || state == BlockByIdState.ALREADY_BLOCKED) {
            kotlinx.coroutines.delay(1_500)
            onDismiss()
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon  = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.block_by_identifier_confirm_title)) },
            text  = { Text(stringResource(R.string.block_by_identifier_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        if (selectedTab == 0) onBlock(phoneInput.trim(), null)
                        else                  onBlock(null, idInput.trim().toLong())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.block_by_identifier_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (state != BlockByIdState.LOADING) onDismiss() },
        icon  = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.block_by_identifier_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text  = stringResource(R.string.block_by_identifier_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Tab row: Телефон / ID
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        icon     = { Icon(Icons.Default.Phone, contentDescription = null) },
                        text     = { Text(stringResource(R.string.block_by_identifier_tab_phone)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        icon     = { Icon(Icons.Default.Tag, contentDescription = null) },
                        text     = { Text(stringResource(R.string.block_by_identifier_tab_id)) },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value         = phoneInput,
                        onValueChange = { phoneInput = it },
                        label         = { Text(stringResource(R.string.block_by_phone_label)) },
                        placeholder   = { Text(stringResource(R.string.block_by_phone_hint)) },
                        leadingIcon   = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine    = true,
                        isError       = phoneInput.isNotEmpty() && !phoneValid,
                        supportingText = if (phoneInput.isNotEmpty() && !phoneValid) {
                            { Text(stringResource(R.string.block_by_identifier_invalid_phone)) }
                        } else null,
                        modifier      = Modifier.fillMaxWidth(),
                        enabled       = state != BlockByIdState.LOADING,
                    )
                } else {
                    OutlinedTextField(
                        value         = idInput,
                        onValueChange = { idInput = it.filter { c -> c.isDigit() } },
                        label         = { Text(stringResource(R.string.block_by_id_label)) },
                        placeholder   = { Text(stringResource(R.string.block_by_id_hint)) },
                        leadingIcon   = { Icon(Icons.Default.Tag, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine    = true,
                        isError       = idInput.isNotEmpty() && !idValid,
                        supportingText = if (idInput.isNotEmpty() && !idValid) {
                            { Text(stringResource(R.string.block_by_identifier_invalid_id)) }
                        } else null,
                        modifier      = Modifier.fillMaxWidth(),
                        enabled       = state != BlockByIdState.LOADING,
                    )
                }

                // State feedback
                AnimatedVisibility(visible = state != BlockByIdState.IDLE && state != BlockByIdState.LOADING) {
                    val (text, color) = when (state) {
                        BlockByIdState.SUCCESS        -> stringResource(R.string.block_by_identifier_success)    to MaterialTheme.colorScheme.primary
                        BlockByIdState.ALREADY_BLOCKED -> stringResource(R.string.block_by_identifier_already)   to MaterialTheme.colorScheme.secondary
                        BlockByIdState.NOT_FOUND       -> stringResource(R.string.block_by_identifier_not_found) to MaterialTheme.colorScheme.error
                        BlockByIdState.RATE_LIMITED    -> stringResource(R.string.block_by_identifier_rate_limit) to MaterialTheme.colorScheme.error
                        else                           -> stringResource(R.string.block_by_identifier_error)     to MaterialTheme.colorScheme.error
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = text, color = color, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (state == BlockByIdState.LOADING) {
                Box(
                    modifier            = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    contentAlignment    = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            } else {
                Button(
                    onClick  = {
                        if (!inputValid) return@Button
                        showConfirm = true
                    },
                    enabled  = inputValid,
                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.block_by_identifier_action))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick  = onDismiss,
                enabled  = state != BlockByIdState.LOADING,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
