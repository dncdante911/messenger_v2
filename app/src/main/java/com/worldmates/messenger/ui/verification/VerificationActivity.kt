package com.worldmates.messenger.ui.verification

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.chats.ChatsActivity
import com.worldmates.messenger.ui.components.GradientButton
import com.worldmates.messenger.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.worldmates.messenger.utils.LanguageManager

class VerificationActivity : AppCompatActivity() {

    private lateinit var viewModel: VerificationViewModel

    // Параметры из intent
    private var verificationType: String = "" // "email" или "phone"
    private var contactInfo: String = ""      // email или номер телефона
    private var username: String = ""
    private var password: String = ""
    private var isRegistration: Boolean = true // true - регистрация, false - логин

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Дозволяємо Compose керувати window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Получаем параметры из intent
        verificationType = intent.getStringExtra("verification_type") ?: "email"
        contactInfo = intent.getStringExtra("contact_info") ?: ""
        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""
        isRegistration = intent.getBooleanExtra("is_registration", true)

        viewModel = ViewModelProvider(this).get(VerificationViewModel::class.java)

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        setContent {
            WorldMatesThemedApp {
                VerificationScreen(
                    viewModel = viewModel,
                    verificationType = verificationType,
                    contactInfo = contactInfo,
                    username = username,
                    onVerificationSuccess = { navigateToChats() },
                    onBackPressed = { finish() }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.verificationState.collect { state ->
                when (state) {
                    is VerificationState.Success -> {
                        Toast.makeText(
                            this@VerificationActivity,
                            getString(R.string.verification_success_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToChats()
                    }
                    is VerificationState.Error -> {
                        Toast.makeText(
                            this@VerificationActivity,
                            state.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {}
                }
            }
        }

        // Автоматично відправляємо код після реєстрації
        // access_token вже збережений у UserSession перед навігацією сюди
        if (isRegistration && contactInfo.isNotEmpty()) {
            viewModel.sendVerificationCode(verificationType, contactInfo, username.ifEmpty { null })
        }
    }

    private fun navigateToChats() {
        startActivity(Intent(this, ChatsActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VerificationScreen(
    viewModel: VerificationViewModel,
    verificationType: String,
    contactInfo: String,
    username: String = "",
    onVerificationSuccess: () -> Unit,
    onBackPressed: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val verificationState by viewModel.verificationState.collectAsState()
    val isLoading = verificationState is VerificationState.Loading
    val resendTimer by viewModel.resendTimer.collectAsState()
    val canResend = resendTimer == 0

    val colorScheme = MaterialTheme.colorScheme

    // Анимация фона
    val infiniteTransition = rememberInfiniteTransition()
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GradientStart,
                        GradientEnd,
                        WMPrimary
                    ),
                    startY = gradientOffset,
                    endY = gradientOffset + 1000f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Кнопка назад
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Иконка
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                color = Color.White
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (verificationType == "email") Icons.Default.Email else Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = WMPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Заголовок
            Text(
                stringResource(R.string.verification_screen_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Описание
            Text(
                text = if (verificationType == "email") {
                    stringResource(R.string.code_sent_email_format, contactInfo)
                } else {
                    stringResource(R.string.code_sent_sms_format, contactInfo)
                },
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Поля ввода кода
            CodeInputField(
                code = code,
                onCodeChange = { newCode ->
                    if (newCode.length <= 6) {
                        code = newCode
                        // Автоматическая верификация при вводе 6 цифр
                        if (newCode.length == 6) {
                            viewModel.verifyCode(verificationType, contactInfo, newCode, username)
                        }
                    }
                },
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Кнопка подтверждения
            GradientButton(
                text = stringResource(R.string.verify),
                onClick = {
                    if (code.length == 6) {
                        viewModel.verifyCode(verificationType, contactInfo, code, username)
                    }
                },
                enabled = code.length == 6 && !isLoading,
                isLoading = isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Повторная отправка
            if (canResend) {
                TextButton(
                    onClick = {
                        viewModel.resendCode(verificationType, contactInfo, username)
                    }
                ) {
                    Text(
                        stringResource(R.string.resend_code_full),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    stringResource(R.string.resend_code_timer_format, resendTimer),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }

            // Error message
            if (verificationState is VerificationState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.95f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = (verificationState as VerificationState.Error).message,
                            color = colorScheme.error,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeInputField(
    code: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        // Додаємо невеликий delay щоб view встигли розміститися
        delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ігноруємо помилки фокусування
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(6) { index ->
            val char = code.getOrNull(index)?.toString() ?: ""
            val isFocused = code.length == index

            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation = if (isFocused) 8.dp else 4.dp,
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                tonalElevation = if (isFocused) 4.dp else 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        text = char,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (char.isNotEmpty()) colorScheme.onSurface else Color.Gray.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }

    // Невидимое текстовое поле для ввода
    BasicTextField(
        value = code,
        onValueChange = { newValue ->
            if (newValue.all { it.isDigit() }) {
                onCodeChange(newValue)
            }
        },
        modifier = Modifier
            .size(0.dp)
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = enabled
    )
}
