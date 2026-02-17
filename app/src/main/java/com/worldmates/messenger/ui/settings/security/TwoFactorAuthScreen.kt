package com.worldmates.messenger.ui.settings.security

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.utils.security.SecurePreferences
import com.worldmates.messenger.utils.security.TOTPGenerator
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorAuthScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val is2FAEnabled = remember { mutableStateOf(SecurePreferences.is2FAEnabled) }
    val currentStep = remember { mutableStateOf(TwoFAStep.MAIN) }

    // Данные для настройки 2FA
    val secret = remember { mutableStateOf("") }
    val qrCode = remember { mutableStateOf<Bitmap?>(null) }
    val verificationCode = remember { mutableStateOf("") }
    val recoveryCodes = remember { mutableStateOf<List<String>>(emptyList()) }
    val showError = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }

    // Анимация появления
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
                            "Двофакторна аутентифікація",
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )

                // Content
                when (currentStep.value) {
                    TwoFAStep.MAIN -> {
                        MainStep(
                            is2FAEnabled = is2FAEnabled.value,
                            onEnableClick = {
                                // Генерируем секретный ключ и QR-код
                                secret.value = TOTPGenerator.generateSecret()
                                qrCode.value = TOTPGenerator.generateQRCode(
                                    secret = secret.value,
                                    accountName = UserSession.username ?: "User",
                                    issuer = "WorldMates"
                                )
                                currentStep.value = TwoFAStep.SETUP
                            },
                            onDisableClick = {
                                currentStep.value = TwoFAStep.DISABLE
                            }
                        )
                    }

                    TwoFAStep.SETUP -> {
                        SetupStep(
                            qrCode = qrCode.value,
                            secret = secret.value,
                            verificationCode = verificationCode.value,
                            onCodeChange = {
                                verificationCode.value = it
                                showError.value = false
                            },
                            showError = showError.value,
                            errorMessage = errorMessage.value,
                            onVerifyClick = {
                                // Проверяем введенный код
                                if (TOTPGenerator.verifyTOTP(secret.value, verificationCode.value)) {
                                    // Генерируем recovery коды
                                    recoveryCodes.value = TOTPGenerator.generateRecoveryCodes()

                                    // Сохраняем данные
                                    SecurePreferences.twoFASecret = secret.value
                                    SecurePreferences.saveBackupCodes(recoveryCodes.value)

                                    currentStep.value = TwoFAStep.RECOVERY_CODES
                                } else {
                                    showError.value = true
                                    errorMessage.value = "Невірний код. Спробуйте ще раз."
                                }
                            },
                            onBackClick = { currentStep.value = TwoFAStep.MAIN }
                        )
                    }

                    TwoFAStep.RECOVERY_CODES -> {
                        RecoveryCodesStep(
                            codes = recoveryCodes.value,
                            onDoneClick = {
                                // Включаем 2FA
                                SecurePreferences.is2FAEnabled = true
                                is2FAEnabled.value = true
                                currentStep.value = TwoFAStep.MAIN
                            }
                        )
                    }

                    TwoFAStep.DISABLE -> {
                        DisableStep(
                            onConfirmClick = {
                                SecurePreferences.clear2FAData()
                                is2FAEnabled.value = false
                                currentStep.value = TwoFAStep.MAIN
                            },
                            onCancelClick = {
                                currentStep.value = TwoFAStep.MAIN
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainStep(
    is2FAEnabled: Boolean,
    onEnableClick: () -> Unit,
    onDisableClick: () -> Unit
) {
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
                                    colors = if (is2FAEnabled) {
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
                            imageVector = if (is2FAEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2FA ${if (is2FAEnabled) "Увімкнено" else "Вимкнено"}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C3E50)
                        )
                        Text(
                            text = if (is2FAEnabled) {
                                "Ваш обліковий запис захищено"
                            } else {
                                "Додатковий захист для входу"
                            },
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Description
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF0084FF),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Що таке 2FA?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Двофакторна аутентифікація додає додатковий рівень безпеки. " +
                        "Крім пароля, вам потрібно буде ввести 6-значний код з Google Authenticator.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Action Button
        item {
            Button(
                onClick = if (is2FAEnabled) onDisableClick else onEnableClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (is2FAEnabled) Color.Red else Color(0xFF0084FF)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = if (is2FAEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (is2FAEnabled) "Вимкнути 2FA" else "Увімкнути 2FA",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Backup codes info (if enabled)
        if (is2FAEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = Color(0xFF0084FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Резервні коди",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C3E50)
                            )
                            Text(
                                "Залишилось: ${SecurePreferences.getRemainingBackupCodesCount()} / 10",
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
    }
}

@Composable
private fun SetupStep(
    qrCode: Bitmap?,
    secret: String,
    verificationCode: String,
    onCodeChange: (String) -> Unit,
    showError: Boolean,
    errorMessage: String,
    onVerifyClick: () -> Unit,
    onBackClick: () -> Unit
) {
    // TOTP timer
    var remainingSeconds by remember { mutableStateOf(TOTPGenerator.getRemainingSeconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            remainingSeconds = TOTPGenerator.getRemainingSeconds()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step 1: Scan QR Code
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Крок 1: Скануйте QR-код",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Відкрийте Google Authenticator та скануйте цей код",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // QR Code
                    qrCode?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(250.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Manual entry
                    Text(
                        "Або введіть код вручну:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        secret,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0084FF),
                        modifier = Modifier
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
            }
        }

        // Step 2: Enter verification code
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Крок 2: Введіть код",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Введіть 6-значний код з Google Authenticator",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { if (it.length <= 6) onCodeChange(it) },
                        label = { Text("Код підтвердження") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = showError,
                        supportingText = if (showError) {
                            { Text(errorMessage, color = Color.Red) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Timer
                    LinearProgressIndicator(
                        progress = remainingSeconds / 30f,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0084FF)
                    )
                    Text(
                        "Код оновиться через $remainingSeconds сек",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Назад")
                }

                Button(
                    onClick = onVerifyClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = verificationCode.length == 6,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0084FF)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Підтвердити", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RecoveryCodesStep(
    codes: List<String>,
    onDoneClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Збережіть ці коди!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF856404)
                        )
                        Text(
                            "Використовуйте їх, якщо втратите доступ до Google Authenticator",
                            fontSize = 13.sp,
                            color = Color(0xFF856404)
                        )
                    }
                }
            }
        }

        // Recovery codes
        items(codes) { code ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = Color(0xFF0084FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        code,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50),
                        modifier = Modifier.weight(1f),
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        item {
            Button(
                onClick = onDoneClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Я зберіг коди", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DisableStep(
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Вимкнути 2FA?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Ваш обліковий запис стане менш захищеним. " +
                    "Ви дійсно хочете вимкнути двофакторну аутентифікацію?",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Скасувати")
                    }

                    Button(
                        onClick = onConfirmClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Вимкнути", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private enum class TwoFAStep {
    MAIN, SETUP, RECOVERY_CODES, DISABLE
}
