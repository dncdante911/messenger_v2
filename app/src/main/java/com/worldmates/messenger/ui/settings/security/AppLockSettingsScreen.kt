package com.worldmates.messenger.ui.settings.security

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.security.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    activity: FragmentActivity,
    onBackClick: () -> Unit
) {
    val biometricManager = remember { BiometricAuthManager(activity) }
    val biometricAvailability = remember { biometricManager.isBiometricAvailable() }

    val isPINEnabled = remember { mutableStateOf(SecurePreferences.isPINEnabled()) }
    val isBiometricEnabled = remember { mutableStateOf(SecurePreferences.isBiometricEnabled) }
    val appLockTimeout = remember { mutableStateOf(SecurePreferences.appLockTimeout) }

    val showPINSetup = remember { mutableStateOf(false) }
    val showTimeoutDialog = remember { mutableStateOf(false) }
    val showDisablePINDialog = remember { mutableStateOf(false) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667eea),
                        Color(0xFF764ba2)
                    )
                )
            )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_lock),
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = if (isPINEnabled.value || isBiometricEnabled.value) {
                                                    listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))
                                                } else {
                                                    listOf(Color(0xFFFF9800), Color(0xFFFFC107))
                                                }
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPINEnabled.value || isBiometricEnabled.value)
                                            Icons.Default.Lock
                                        else
                                            Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPINEnabled.value || isBiometricEnabled.value) {
                                            stringResource(R.string.app_protected)
                                        } else {
                                            stringResource(R.string.protection_disabled)
                                        },
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2C3E50)
                                    )
                                    Text(
                                        text = if (isPINEnabled.value) {
                                            stringResource(R.string.pin_active_status)
                                        } else if (isBiometricEnabled.value) {
                                            stringResource(R.string.biometric_active_status)
                                        } else {
                                            stringResource(R.string.configure_protection)
                                        },
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // PIN Settings
                    item {
                        Text(
                            stringResource(R.string.pin_code),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isPINEnabled.value) {
                                            showDisablePINDialog.value = true
                                        } else {
                                            showPINSetup.value = true
                                        }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Pin,
                                    contentDescription = null,
                                    tint = Color(0xFF0084FF),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.pin_code),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2C3E50)
                                    )
                                    Text(
                                        if (isPINEnabled.value) stringResource(R.string.two_factor_enabled) else stringResource(R.string.two_factor_disabled),
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = isPINEnabled.value,
                                    onCheckedChange = {
                                        if (it) {
                                            showPINSetup.value = true
                                        } else {
                                            showDisablePINDialog.value = true
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Biometric Settings
                    if (biometricAvailability == BiometricAvailability.AVAILABLE) {
                        item {
                            Text(
                                stringResource(R.string.biometric_auth),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isPINEnabled.value) {
                                                // Сначала нужно установить PIN
                                                showPINSetup.value = true
                                            } else {
                                                isBiometricEnabled.value = !isBiometricEnabled.value
                                                SecurePreferences.isBiometricEnabled = isBiometricEnabled.value
                                            }
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = Color(0xFF0084FF),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            biometricManager.getBiometricTypeName(),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2C3E50)
                                        )
                                        Text(
                                            if (isBiometricEnabled.value) stringResource(R.string.two_factor_enabled) else stringResource(R.string.two_factor_disabled),
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Switch(
                                        checked = isBiometricEnabled.value,
                                        enabled = isPINEnabled.value,
                                        onCheckedChange = {
                                            if (isPINEnabled.value) {
                                                isBiometricEnabled.value = it
                                                SecurePreferences.isBiometricEnabled = it
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF3CD)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.biometric_unavailable),
                                        fontSize = 13.sp,
                                        color = Color(0xFF856404)
                                    )
                                }
                            }
                        }
                    }

                    // Auto-lock timeout
                    if (isPINEnabled.value || isBiometricEnabled.value) {
                        item {
                            Text(
                                stringResource(R.string.lock_app),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showTimeoutDialog.value = true }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = Color(0xFF0084FF),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.lock_after),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2C3E50)
                                        )
                                        Text(
                                            SecurePreferences.getAppLockTimeoutLabel(),
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // Info
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.95f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF0084FF),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.about_app_lock_title),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.about_app_lock_desc),
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PIN Setup Dialog
    if (showPINSetup.value) {
        PINSetupDialog(
            onDismiss = { showPINSetup.value = false },
            onSuccess = {
                isPINEnabled.value = true
                showPINSetup.value = false
            }
        )
    }

    // Timeout Selection Dialog
    if (showTimeoutDialog.value) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog.value = false },
            title = { Text(stringResource(R.string.lock_after)) },
            text = {
                Column {
                    AppLockTimeout.getAll().forEach { (timeout, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appLockTimeout.value = timeout
                                    SecurePreferences.appLockTimeout = timeout
                                    showTimeoutDialog.value = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLockTimeout.value == timeout,
                                onClick = {
                                    appLockTimeout.value = timeout
                                    SecurePreferences.appLockTimeout = timeout
                                    showTimeoutDialog.value = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog.value = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Disable PIN Dialog
    if (showDisablePINDialog.value) {
        AlertDialog(
            onDismissRequest = { showDisablePINDialog.value = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text(stringResource(R.string.disable_pin_title)) },
            text = {
                Text(
                    stringResource(R.string.disable_pin_message),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SecurePreferences.removePIN()
                        SecurePreferences.isBiometricEnabled = false
                        isPINEnabled.value = false
                        isBiometricEnabled.value = false
                        showDisablePINDialog.value = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text(stringResource(R.string.disable_action_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisablePINDialog.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
