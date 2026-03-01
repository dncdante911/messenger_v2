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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.ui.chats.ChatsActivity
import com.worldmates.messenger.ui.components.*
import com.worldmates.messenger.ui.theme.*
import kotlinx.coroutines.launch
import com.worldmates.messenger.utils.LanguageManager

class ForgotPasswordActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            WorldMatesThemedApp {
                AccessRecoveryScreen(
                    onBack = { finish() },
                    onPasswordResetSuccess = {
                        Toast.makeText(this, getString(R.string.change_password), Toast.LENGTH_LONG).show()
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
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.nav_back), tint = Color.White)
                }
                Text(
                    stringResource(R.string.recovery_screen_title),
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
                stringResource(R.string.what_restore_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.choose_recovery_desc),
                fontSize = 14.sp,
                color = colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Forgot password card
            RecoveryOptionCard(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.forgot_password_option),
                subtitle = stringResource(R.string.restore_via_email),
                onClick = onForgotPassword
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Forgot email card
            RecoveryOptionCard(
                icon = Icons.Default.Email,
                title = stringResource(R.string.forgot_email),
                subtitle = stringResource(R.string.restore_via_phone),
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
                    Text(stringResource(R.string.enter_email_or_phone_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.will_send_6digit),
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(16.dp))

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.primary
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.email)) },
                            icon = { Icon(Icons.Default.Email, null, Modifier.size(18.dp)) })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.phone)) },
                            icon = { Icon(Icons.Default.Phone, null, Modifier.size(18.dp)) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedTab == 0) {
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            label = { Text(stringResource(R.string.email)) },
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
                            label = stringResource(R.string.phone_number)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val errorCheckData = stringResource(R.string.error_check_data)
                    GradientButton(
                        text = stringResource(R.string.send_code),
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
                                        errorMessage = response.errorMessage ?: errorCheckData
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.localizedMessage
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
                    Text(stringResource(R.string.enter_confirm_code_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        infoMessage ?: if (selectedTab == 0)
                            stringResource(R.string.code_sent_to_format, email)
                        else
                            stringResource(R.string.code_sent_to_number_format, contact),
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                        label = { Text(stringResource(R.string.enter_verification_code)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !isLoading, singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val code6digitsStr = stringResource(R.string.code_must_be_6_digits)
                    val codeResentStr2 = stringResource(R.string.code_resent)
                    GradientButton(
                        text = stringResource(R.string.continue_btn),
                        onClick = {
                            if (code.length == 6) {
                                errorMessage = null
                                step = ResetStep.NEW_PASSWORD
                            } else {
                                errorMessage = code6digitsStr
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
                                infoMessage = codeResentStr2
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage
                            } finally {
                                isLoading = false
                            }
                        }
                    }) {
                        Text(stringResource(R.string.resend_code_btn), color = colorScheme.primary)
                    }

                    TextButton(onClick = { step = ResetStep.ENTER_CONTACT; code = ""; errorMessage = null }) {
                        Text(stringResource(R.string.go_back_btn))
                    }
                }

                // ── Step 3: New password ─────────────────────────────────────
                ResetStep.NEW_PASSWORD -> {
                    Text(stringResource(R.string.new_password_screen_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.enter_new_password_desc),
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.new_password)) },
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
                        label = { Text(stringResource(R.string.confirm_password)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isLoading, singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                    )
                    if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                        Text(stringResource(R.string.passwords_not_match), color = colorScheme.error,
                            fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val passMin8Str = stringResource(R.string.password_min_8_chars)
                    val passNotMatchStr = stringResource(R.string.passwords_not_match)
                    val invalidExpiredStr = stringResource(R.string.invalid_expired_code)
                    GradientButton(
                        text = stringResource(R.string.change_password),
                        onClick = {
                            errorMessage = null
                            when {
                                newPassword.length < 8 -> errorMessage = passMin8Str
                                newPassword != confirmPassword -> errorMessage = passNotMatchStr
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
                                                errorMessage = response.errorMessage ?: invalidExpiredStr
                                                if (errorMessage?.contains("code", ignoreCase = true) == true ||
                                                    errorMessage?.contains("код", ignoreCase = true) == true) {
                                                    step = ResetStep.ENTER_CODE
                                                }
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = e.localizedMessage
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
                        stringResource(R.string.phone_recovery_info_text),
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (step) {

                // ── Step 1: Enter phone ──────────────────────────────────────
                ResetStep.ENTER_CONTACT -> {
                    Text(stringResource(R.string.enter_phone_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.phone_linked_hint),
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))

                    Spacer(modifier = Modifier.height(16.dp))

                    PhoneInputField(
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = { phoneNumber = it },
                        selectedCountry = selectedCountry,
                        onCountryChange = { selectedCountry = it },
                        enabled = !isLoading,
                        label = stringResource(R.string.phone_number)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val phoneNotFoundStr = stringResource(R.string.phone_not_found)
                    GradientButton(
                        text = stringResource(R.string.send_code),
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
                                        errorMessage = response.errorMessage ?: phoneNotFoundStr
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.localizedMessage
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
                    Text(stringResource(R.string.enter_confirm_code_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(infoMessage ?: stringResource(R.string.code_sent_to_number_format, fullPhone),
                        fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                        label = { Text(stringResource(R.string.enter_verification_code)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !isLoading, singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val invalidExpiredStr2 = stringResource(R.string.invalid_expired_code)
                    val code6digitsStr2 = stringResource(R.string.code_must_be_6_digits)
                    GradientButton(
                        text = stringResource(R.string.restore_access),
                        onClick = {
                            if (code.length == 6) {
                                errorMessage = null
                                isLoading = true
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
                                            errorMessage = response.errorMessage ?: invalidExpiredStr2
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else {
                                errorMessage = code6digitsStr2
                            }
                        },
                        enabled = code.length == 6 && !isLoading,
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { step = ResetStep.ENTER_CONTACT; code = ""; errorMessage = null }) {
                        Text(stringResource(R.string.go_back_btn))
                    }
                }

                // ── Step 3: Show temp password ───────────────────────────────
                ResetStep.SHOW_TEMP_PASSWORD -> {
                    Text(stringResource(R.string.temp_password_created_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.account_restored_temp_pass),
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
                            Text(stringResource(R.string.temp_password_label), fontSize = 13.sp,
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
                                Text(stringResource(R.string.copy_password_btn))
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
                                stringResource(R.string.change_password_hint_after_login),
                                fontSize = 13.sp, color = colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientButton(
                        text = stringResource(R.string.go_to_login),
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
