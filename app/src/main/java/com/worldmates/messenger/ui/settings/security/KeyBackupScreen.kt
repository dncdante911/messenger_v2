package com.worldmates.messenger.ui.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmates.messenger.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyBackupScreen(
    onBackClick: () -> Unit,
    viewModel: KeyBackupViewModel = viewModel(),
) {
    val state        by viewModel.state.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()

    var password        by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var showPassword    by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var snackMessage    by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadBackupStatus() }

    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackMessage = null
        }
    }

    val isLoading = state is KeyBackupViewModel.UiState.Loading

    val successCreate  = stringResource(R.string.key_backup_success_create)
    val successRestore = stringResource(R.string.key_backup_success_restore)
    val successDelete  = stringResource(R.string.key_backup_success_delete)
    val errorNoKeys    = stringResource(R.string.key_backup_error_no_keys)
    val errorWrongPwd  = stringResource(R.string.key_backup_error_wrong_password)
    val errorNoBackup  = stringResource(R.string.key_backup_error_no_backup)

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text(stringResource(R.string.key_backup_delete)) },
            text    = { Text(stringResource(R.string.key_backup_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteBackup(
                        onSuccess = { snackMessage = successDelete },
                        onError   = { snackMessage = it }
                    )
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.key_backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Status card ──────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = if (backupStatus.exists)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (backupStatus.exists) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (backupStatus.exists)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text  = stringResource(if (backupStatus.exists) R.string.key_backup_exists else R.string.key_backup_none),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        if (backupStatus.exists && backupStatus.updatedAt > 0) {
                            val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                            Text(
                                text  = stringResource(R.string.key_backup_last_updated, fmt.format(Date(backupStatus.updatedAt * 1000))),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Description ──────────────────────────────────────────────────
            Text(
                text  = stringResource(R.string.key_backup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Warning ──────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(
                        text  = stringResource(R.string.key_backup_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            HorizontalDivider()

            // ── Create / Update section ──────────────────────────────────────
            Text(
                text       = stringResource(R.string.key_backup_section_setup),
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value         = password,
                onValueChange = { password = it },
                label         = { Text(stringResource(R.string.key_backup_password_hint)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon  = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                supportingText = { Text(stringResource(R.string.key_backup_password_min)) }
            )

            OutlinedTextField(
                value         = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label         = { Text(stringResource(R.string.key_backup_password_confirm_hint)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                isError = passwordConfirm.isNotEmpty() && password != passwordConfirm,
                supportingText = {
                    if (passwordConfirm.isNotEmpty() && password != passwordConfirm) {
                        Text(stringResource(R.string.key_backup_password_mismatch),
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            val canCreate = password.length >= 8 && password == passwordConfirm
            Button(
                onClick  = {
                    viewModel.createBackup(
                        password  = password,
                        onSuccess = {
                            snackMessage = successCreate
                            password = ""
                            passwordConfirm = ""
                        },
                        onError = { err ->
                            snackMessage = when (err) {
                                "no_keys" -> errorNoKeys
                                else      -> err
                            }
                        }
                    )
                },
                enabled  = canCreate && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (backupStatus.exists) R.string.key_backup_update else R.string.key_backup_create))
            }

            HorizontalDivider()

            // ── Restore section ──────────────────────────────────────────────
            Text(
                text       = stringResource(R.string.key_backup_section_restore),
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.primary,
            )

            OutlinedButton(
                onClick  = {
                    if (password.isBlank()) { snackMessage = errorNoKeys; return@OutlinedButton }
                    viewModel.restoreBackup(
                        password  = password,
                        onSuccess = {
                            snackMessage = successRestore
                            password = ""
                            passwordConfirm = ""
                        },
                        onError = { err ->
                            snackMessage = when (err) {
                                "wrong_password" -> errorWrongPwd
                                "no_backup"      -> errorNoBackup
                                else             -> err
                            }
                        }
                    )
                },
                enabled  = password.length >= 8 && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.key_backup_restore))
            }

            if (backupStatus.exists) {
                TextButton(
                    onClick  = { showDeleteDialog = true },
                    enabled  = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.key_backup_delete))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
