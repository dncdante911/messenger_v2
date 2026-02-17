package com.worldmates.messenger.ui.settings.security

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worldmates.messenger.utils.security.SecurePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Диалог для установки PIN-кода
 */
@Composable
fun PINSetupDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(PINSetupStep.CREATE) }
    var firstPIN by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
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
            when (step) {
                PINSetupStep.CREATE -> {
                    PINInputScreen(
                        title = "Створити PIN-код",
                        subtitle = "Введіть 4-значний PIN-код",
                        onPINEntered = { pin ->
                            firstPIN = pin
                            step = PINSetupStep.CONFIRM
                        },
                        onBackClick = onDismiss,
                        showError = showError
                    )
                }
                PINSetupStep.CONFIRM -> {
                    PINInputScreen(
                        title = "Підтвердити PIN-код",
                        subtitle = "Введіть PIN-код ще раз",
                        onPINEntered = { pin ->
                            if (pin == firstPIN) {
                                // Сохраняем PIN
                                SecurePreferences.savePIN(pin)
                                onSuccess()
                            } else {
                                showError = true
                                step = PINSetupStep.CREATE
                                firstPIN = ""
                            }
                        },
                        onBackClick = {
                            step = PINSetupStep.CREATE
                            firstPIN = ""
                        },
                        showError = showError
                    )
                }
            }
        }
    }
}

/**
 * Экран ввода PIN-кода (используется для разблокировки приложения)
 */
@Composable
fun PINLockScreen(
    onUnlocked: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
    showBiometricOption: Boolean = false
) {
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

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
        PINInputScreen(
            title = "Введіть PIN-код",
            subtitle = "Розблокуйте додаток",
            onPINEntered = { pin ->
                if (SecurePreferences.verifyPIN(pin)) {
                    SecurePreferences.updateUnlockTime()
                    onUnlocked()
                } else {
                    showError = true
                    errorMessage = "Невірний PIN-код"
                }
            },
            showError = showError,
            errorMessage = errorMessage,
            showBiometricButton = showBiometricOption,
            onBiometricClick = onBiometricClick
        )
    }
}

/**
 * Компонент для ввода PIN-кода
 */
@Composable
private fun PINInputScreen(
    title: String,
    subtitle: String,
    onPINEntered: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    showError: Boolean = false,
    errorMessage: String = "PIN-коди не збігаються",
    showBiometricButton: Boolean = false,
    onBiometricClick: (() -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Сброс ошибки при изменении PIN
    LaunchedEffect(pin) {
        if (pin.isNotEmpty() && showError) {
            // Ошибка будет сброшена снаружи
        }
    }

    // Анимация shake при ошибке
    var shake by remember { mutableStateOf(false) }
    LaunchedEffect(showError) {
        if (showError) {
            shake = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(500)
            shake = false
            pin = ""
        }
    }

    val shakeOffset by animateFloatAsState(
        targetValue = if (shake) 20f else 0f,
        animationSpec = repeatable(
            iterations = 3,
            animation = tween(50),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // PIN Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shakeOffset.dp)
            ) {
                repeat(4) { index ->
                    PINDot(filled = index < pin.length, error = showError)
                }
            }

            // Error message
            AnimatedVisibility(
                visible = showError,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    errorMessage,
                    fontSize = 14.sp,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Keypad
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Biometric button (if available)
            if (showBiometricButton && onBiometricClick != null) {
                OutlinedButton(
                    onClick = onBiometricClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Використати біометрію", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Number pad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Row 1-3
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                ).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { number ->
                            PINButton(
                                text = number,
                                onClick = {
                                    if (pin.length < 4) {
                                        pin += number
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                        // Автоматическая отправка при вводе 4 цифр
                                        if (pin.length == 4) {
                                            scope.launch {
                                                delay(100)
                                                onPINEntered(pin)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Row 4
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Back button or empty
                    if (onBackClick != null) {
                        PINButton(
                            text = "←",
                            onClick = onBackClick,
                            isSpecial = true
                        )
                    } else {
                        Spacer(modifier = Modifier.size(72.dp))
                    }

                    PINButton(
                        text = "0",
                        onClick = {
                            if (pin.length < 4) {
                                pin += "0"
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                if (pin.length == 4) {
                                    scope.launch {
                                        delay(100)
                                        onPINEntered(pin)
                                    }
                                }
                            }
                        }
                    )

                    PINButton(
                        text = "⌫",
                        onClick = {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        isSpecial = true
                    )
                }
            }
        }
    }
}

@Composable
private fun PINDot(filled: Boolean, error: Boolean = false) {
    var scale by remember { mutableStateOf(1f) }

    LaunchedEffect(filled) {
        if (filled) {
            scale = 1.3f
            delay(100)
            scale = 1f
        }
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (error) Color.Red else Color.White,
                shape = CircleShape
            )
            .background(
                color = if (filled) {
                    if (error) Color.Red else Color.White
                } else {
                    Color.Transparent
                }
            )
    )
}

@Composable
private fun PINButton(
    text: String,
    onClick: () -> Unit,
    isSpecial: Boolean = false
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                color = if (isSpecial) {
                    Color.White.copy(alpha = 0.1f)
                } else {
                    Color.White.copy(alpha = 0.2f)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    pressed = false
                    onClick()
                }
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pressed = event.changes.any { it.pressed }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (isSpecial) 28.sp else 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private enum class PINSetupStep {
    CREATE, CONFIRM
}
