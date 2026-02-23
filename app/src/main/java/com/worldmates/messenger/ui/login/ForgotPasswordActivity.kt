package com.worldmates.messenger.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.ui.chats.ChatsActivity
import com.worldmates.messenger.ui.components.*
import com.worldmates.messenger.ui.theme.*
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            WorldMatesThemedApp {
                AccessRecoveryScreen(
                    onBack = { finish() },
                    onPasswordResetSuccess = {
                        Toast.makeText(this, "Пароль успішно змінено! Увійдіть з новим паролем.", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onEmailRecoverySuccess = {
                        startActivity(Intent(this, ChatsActivity::class.java))
                        finishAffinity()
                    }
                )
            }
        }
    }
}

// ─── Enums ────────────────────────────────────────────────────────────────────

enum class RecoveryMode {
    CHOOSE,           // вибір типу відновлення
    FORGOT_PASSWORD,  // забув пароль
    FORGOT_EMAIL      // забув email (відновлення по телефону)
}

enum class ResetStep {
    ENTER_CONTACT,
    ENTER_CODE,
    NEW_PASSWORD,
    SHOW_TEMP_PASSWORD
}

// ─── Root composable ──────────────────────────────────────────────────────────

@Composable
fun AccessRecoveryScreen(
    onBack: () -> Unit,
    onPasswordResetSuccess: () -> Unit,
    onEmailRecoverySuccess: () -> Unit
) {
    var mode by remember { mutableStateOf(RecoveryMode.CHOOSE) }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF090D1A), Color(0xFF121539), Color(0xFF0A0F24)),
                    startY = gradientOffset * 0.05f,
                    endY   = gradientOffset * 0.05f + 2000f
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF1565C0).copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.06f), radius = 320f
                    ),
                    radius = 320f, center = Offset(size.width * 0.85f, size.height * 0.06f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF6A1B9A).copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.78f), radius = 260f
                    ),
                    radius = 260f, center = Offset(size.width * 0.12f, size.height * 0.78f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Back button + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (mode == RecoveryMode.CHOOSE) onBack()
                    else mode = RecoveryMode.CHOOSE
                }) {
                    Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                }
                Text(
                    "Відновлення доступу",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (mode) {
                RecoveryMode.CHOOSE -> ChooseModeCard(
                    onForgotPassword = { mode = RecoveryMode.FORGOT_PASSWORD },
                    onForgotEmail = { mode = RecoveryMode.FORGOT_EMAIL }
                )

                RecoveryMode.FORGOT_PASSWORD -> ForgotPasswordFlow(
                    onSuccess = onPasswordResetSuccess,
                    gradientOffset = gradientOffset
                )

                RecoveryMode.FORGOT_EMAIL -> ForgotEmailFlow(
                    onSuccess = onEmailRecoverySuccess,
                    gradientOffset = gradientOffset
                )
            }
        }
    }
}

// ─── Choose Mode Card ─────────────────────────────────────────────────────────

@Composable
fun ChooseModeCard(
    onForgotPassword: () -> Unit,
    onForgotEmail: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Що саме ви хочете відновити?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Оберіть варіант відновлення доступу до вашого акаунту.",
                fontSize = 14.sp,
                color = colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Forgot password card
            RecoveryOptionCard(
                icon = Icons.Default.Lock,
                title = "Забув пароль",
                subtitle = "Відновити доступ через email або телефон",
                onClick = onForgotPassword
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Forgot email card
            RecoveryOptionCard(
                icon = Icons.Default.Email,
                title = "Забув email",
                subtitle = "Відновити доступ через номер телефону",
                onClick = onForgotEmail
            )
        }
    }
}

@Composable
fun RecoveryOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                Text(subtitle, fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

// ─── Forgot Password Flow ─────────────────────────────────────────────────────

@Composable
fun ForgotPasswordFlow(
    onSuccess: () -> Unit,
    gradientOffset: Float
) {
    var step by remember { mutableStateOf(ResetStep.ENTER_CONTACT) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=email, 1=phone
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(popularCountries[0]) }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // Computed on every recomposition via state reads
    val contact = if (selectedTab == 0) email
                  else getFullPhoneNumber(selectedCountry.dialCode, phoneNumber)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            when (step) {

                // ── Step 1: Enter contact ────────────────────────────────────
                ResetStep.ENTER_CONTACT -> {
                    Text("Введіть email або телефон", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Ми надішлемо 6-значний код підтвердження",
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(16.dp))

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.primary
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            text = { Text("Email") },
                            icon = { Icon(Icons.Default.Email, null, Modifier.size(18.dp)) })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            text = { Text("Телефон") },
                            icon = { Icon(Icons.Default.Phone, null, Modifier.size(18.dp)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedTab == 0) {
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled = !isLoading, singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        PhoneInputField(
                            phoneNumber = phoneNumber,
                            onPhoneNumberChange = { phoneNumber = it },
                            selectedCountry = selectedCountry,
                            onCountryChange = { selectedCountry = it },
                            enabled = !isLoading,
                            label = "Номер телефону"
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientButton(
                        text = "Надіслати код",
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            scope.launch {
                                try {
                                    val response = NodeRetrofitClient.api.requestPasswordReset(
                                        email = if (selectedTab == 0) contact else null,
                                        phoneNumber = if (selectedTab == 1) contact else null
                                    )
                                    if (response.apiStatus == 200) {
                                        infoMessage = response.message
                                        step = ResetStep.ENTER_CODE
                                    } else {
                                        errorMessage = response.errorMessage ?: "Помилка. Перевірте введені дані."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Помилка мережі: ${e.localizedMessage}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = (selectedTab == 0 && email.isNotEmpty()) ||
                                (selectedTab == 1 && phoneNumber.isNotEmpty()),
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Step 2: Enter code ───────────────────────────────────────
                ResetStep.ENTER_CODE -> {
                    Text("Введіть код підтвердження", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        infoMessage ?: if (selectedTab == 0)
                            "Код надіслано на $email"
                        else
                            "Код надіслано на номер $contact",
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                        label = { Text("6-значний код") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !isLoading, singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientButton(
                        text = "Продовжити",
                        onClick = {
                            if (code.length == 6) {
                                errorMessage = null
                                step = ResetStep.NEW_PASSWORD
                            } else {
                                errorMessage = "Код має містити 6 цифр"
                            }
                        },
                        enabled = code.length == 6 && !isLoading,
                        isLoading = false,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Resend code
                    TextButton(onClick = {
                        errorMessage = null
                        isLoading = true
                        scope.launch {
                            try {
                                NodeRetrofitClient.api.requestPasswordReset(
                                    email = if (selectedTab == 0) contact else null,
                                    phoneNumber = if (selectedTab == 1) contact else null
                                )
                                infoMessage = "Код надіслано повторно"
                            } catch (e: Exception) {
                                errorMessage = "Помилка: ${e.localizedMessage}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }) {
                        Text("Надіслати код повторно", color = colorScheme.primary)
                    }

                    TextButton(onClick = { step = ResetStep.ENTER_CONTACT; code = ""; errorMessage = null }) {
                        Text("Повернутись назад")
                    }
                }

                // ── Step 3: New password ─────────────────────────────────────
                ResetStep.NEW_PASSWORD -> {
                    Text("Новий пароль", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Введіть новий пароль для вашого акаунту",
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it },
                        label = { Text("Новий пароль (мін. 8 символів)") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isLoading, singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it },
                        label = { Text("Підтвердіть пароль") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isLoading, singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                    )
                    if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                        Text("Паролі не збігаються", color = colorScheme.error,
                            fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientButton(
                        text = "Змінити пароль",
                        onClick = {
                            errorMessage = null
                            when {
                                newPassword.length < 8 -> errorMessage = "Пароль має бути не менше 8 символів"
                                newPassword != confirmPassword -> errorMessage = "Паролі не збігаються"
                                else -> {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val response = NodeRetrofitClient.api.resetPassword(
                                                email = if (selectedTab == 0) contact else null,
                                                phoneNumber = if (selectedTab == 1) contact else null,
                                                code = code,
                                                newPassword = newPassword
                                            )
                                            if (response.apiStatus == 200) {
                                                onSuccess()
                                            } else {
                                                errorMessage = response.errorMessage
                                                    ?: "Невірний або прострочений код. Спробуйте ще раз."
                                                // Якщо код невірний — повертаємо на крок 2
                                                if (errorMessage?.contains("code", ignoreCase = true) == true ||
                                                    errorMessage?.contains("код", ignoreCase = true) == true) {
                                                    step = ResetStep.ENTER_CODE
                                                }
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Помилка мережі: ${e.localizedMessage}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && !isLoading,
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {}
            }

            // Error / info messages
            ErrorAndInfoMessages(errorMessage = errorMessage, infoMessage = infoMessage)
        }
    }
}

// ─── Forgot Email Flow (recovery via phone) ───────────────────────────────────

@Composable
fun ForgotEmailFlow(
    onSuccess: () -> Unit,
    gradientOffset: Float
) {
    var step by remember { mutableStateOf(ResetStep.ENTER_CONTACT) }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(popularCountries[0]) }
    var code by remember { mutableStateOf("") }
    var tempPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // Computed on every recomposition via state reads
    val fullPhone = getFullPhoneNumber(selectedCountry.dialCode, phoneNumber)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {

            // Info banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Для відновлення доступу через телефон вам буде надіслано тимчасовий 8-символьний пароль на пошту або SMS.",
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (step) {

                // ── Step 1: Enter phone ──────────────────────────────────────
                ResetStep.ENTER_CONTACT -> {
                    Text("Введіть номер телефону", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Введіть номер телефону, який прив'язаний до вашого акаунту",
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))

                    Spacer(modifier = Modifier.height(16.dp))

                    PhoneInputField(
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = { phoneNumber = it },
                        selectedCountry = selectedCountry,
                        onCountryChange = { selectedCountry = it },
                        enabled = !isLoading,
                        label = "Номер телефону"
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientButton(
                        text = "Надіслати код",
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            scope.launch {
                                try {
                                    val response = NodeRetrofitClient.api.requestPasswordReset(
                                        phoneNumber = fullPhone
                                    )
                                    if (response.apiStatus == 200) {
                                        infoMessage = response.message
                                        step = ResetStep.ENTER_CODE
                                    } else {
                                        errorMessage = response.errorMessage
                                            ?: "Телефон не знайдено в системі."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Помилка мережі: ${e.localizedMessage}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = phoneNumber.isNotEmpty() && !isLoading,
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Step 2: Enter code ───────────────────────────────────────
                ResetStep.ENTER_CODE -> {
                    Text("Введіть код підтвердження", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(infoMessage ?: "Код надіслано на номер $fullPhone",
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                        label = { Text("6-значний код") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !isLoading, singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientButton(
                        text = "Відновити доступ",
                        onClick = {
                            if (code.length == 6) {
                                errorMessage = null
                                isLoading = true
                                // Генеруємо 8-символьний тимчасовий пароль
                                val generated = generateTempPassword(8)
                                scope.launch {
                                    try {
                                        val response = NodeRetrofitClient.api.resetPassword(
                                            phoneNumber = fullPhone,
                                            code = code,
                                            newPassword = generated
                                        )
                                        if (response.apiStatus == 200) {
                                            tempPassword = generated
                                            // Спробуємо автоматично увійти
                                            try {
                                                val loginResponse = RetrofitClient.apiService.login(
                                                    username = fullPhone,
                                                    password = generated
                                                )
                                                if (loginResponse.apiStatus == 200 &&
                                                    loginResponse.accessToken != null &&
                                                    loginResponse.userId != null) {
                                                    UserSession.saveSession(
                                                        loginResponse.accessToken!!,
                                                        loginResponse.userId!!.toLong(),
                                                        loginResponse.username,
                                                        loginResponse.avatar
                                                    )
                                                    onSuccess()
                                                    return@launch
                                                }
                                            } catch (_: Exception) { /* auto-login failed, show temp pass */ }
                                            step = ResetStep.SHOW_TEMP_PASSWORD
                                        } else {
                                            errorMessage = response.errorMessage
                                                ?: "Невірний або прострочений код."
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Помилка мережі: ${e.localizedMessage}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else {
                                errorMessage = "Код має містити 6 цифр"
                            }
                        },
                        enabled = code.length == 6 && !isLoading,
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { step = ResetStep.ENTER_CONTACT; code = ""; errorMessage = null }) {
                        Text("Повернутись назад")
                    }
                }

                // ── Step 3: Show temp password ───────────────────────────────
                ResetStep.SHOW_TEMP_PASSWORD -> {
                    Text("Тимчасовий пароль створено", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Ваш акаунт відновлено. Збережіть тимчасовий пароль — він вже надіслано на вашу пошту.",
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Temp password display
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.primaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Тимчасовий пароль:", fontSize = 13.sp,
                                color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                tempPassword,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimaryContainer,
                                letterSpacing = 6.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(tempPassword))
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Копіювати пароль")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, null,
                                tint = colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Після входу рекомендуємо змінити пароль у налаштуваннях профілю.",
                                fontSize = 13.sp, color = colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientButton(
                        text = "Перейти до входу",
                        onClick = { onSuccess() },
                        enabled = true,
                        isLoading = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {}
            }

            // Error / info messages
            ErrorAndInfoMessages(errorMessage = errorMessage, infoMessage = null)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
fun ErrorAndInfoMessages(errorMessage: String?, infoMessage: String?) {
    val colorScheme = MaterialTheme.colorScheme
    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.error.copy(alpha = 0.1f)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(errorMessage, color = colorScheme.error, fontSize = 13.sp)
            }
        }
    }
    if (infoMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(infoMessage, color = colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}

/**
 * Генерує випадковий тимчасовий пароль заданої довжини.
 * Використовує літери (верхній/нижній регістр) + цифри.
 */
fun generateTempPassword(length: Int = 8): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    return (1..length).map { chars.random() }.joinToString("")
}
