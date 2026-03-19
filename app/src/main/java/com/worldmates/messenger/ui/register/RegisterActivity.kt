package com.worldmates.messenger.ui.register

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.chats.ChatsActivity
import com.worldmates.messenger.ui.components.*
import com.worldmates.messenger.ui.theme.*
import kotlinx.coroutines.launch
import com.worldmates.messenger.utils.LanguageManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RegisterActivity : AppCompatActivity() {

    private lateinit var viewModel: RegisterViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Дозволяємо Compose керувати window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel = ViewModelProvider(this).get(RegisterViewModel::class.java)

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        setContent {
            WorldMatesThemedApp {
                RegisterScreen(
                    viewModel = viewModel,
                    onRegisterSuccess = { email, phone, username, password ->
                        navigateToVerification(
                            if (phone.isNotEmpty()) "phone" else "email",
                            if (phone.isNotEmpty()) phone else email,
                            username,
                            password
                        )
                    },
                    onBackToLogin = { finish() }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.registerState.collect { state ->
                when (state) {
                    is RegisterState.Success -> {
                        // Прямий вхід без верифікації
                        navigateToChats()
                    }
                    is RegisterState.VerificationRequired -> {
                        // Переходимо до екрану верифікації
                        navigateToVerification(
                            state.verificationType,
                            state.contactInfo,
                            state.username,
                            ""
                        )
                    }
                    is RegisterState.Error -> {
                        Toast.makeText(
                            this@RegisterActivity,
                            state.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun navigateToChats() {
        startActivity(Intent(this, ChatsActivity::class.java))
        finish()
    }

    private fun navigateToVerification(
        verificationType: String,
        contactInfo: String,
        username: String,
        password: String
    ) {
        val intent = Intent(this, com.worldmates.messenger.ui.verification.VerificationActivity::class.java)
        intent.putExtra("verification_type", verificationType)
        intent.putExtra("contact_info", contactInfo)
        intent.putExtra("username", username)
        intent.putExtra("password", password)
        intent.putExtra("is_registration", true)
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onRegisterSuccess: (email: String, phone: String, username: String, password: String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("male") }
    var selectedCountry by remember { mutableStateOf(com.worldmates.messenger.ui.components.popularCountries[0]) }
    var selectedTab by remember { mutableStateOf(0) }
    val registerState by viewModel.registerState.collectAsState()
    val isLoading = registerState is RegisterState.Loading

    // Убрано дублирование - состояние обрабатывается в lifecycleScope.launch

    // Animated iridescent rotating gradient background
    val bgTransition = rememberInfiniteTransition(label = "bg")
    val gradAngleState = bgTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "gradAngle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val angle = gradAngleState.value
                val cx = size.width / 2f
                val cy = size.height / 2f
                val dist = size.width * 0.85f
                drawRect(color = Color(0xFF060B1A))
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0D1B4B).copy(alpha = 0.90f),
                            Color(0xFF1A0845).copy(alpha = 0.80f),
                            Color(0xFF061A3A).copy(alpha = 0.90f),
                            Color(0xFF0D1B4B).copy(alpha = 0.90f),
                        ),
                        start = Offset(cx + cos(angle) * dist, cy + sin(angle) * dist * 0.6f),
                        end   = Offset(cx - cos(angle) * dist, cy - sin(angle) * dist * 0.6f)
                    ),
                    size = size
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1565C0).copy(alpha = 0.40f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.10f),
                        radius = 390f
                    ),
                    radius = 390f,
                    center = Offset(size.width * 0.85f, size.height * 0.10f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF7B1FA2).copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.78f),
                        radius = 350f
                    ),
                    radius = 350f,
                    center = Offset(size.width * 0.12f, size.height * 0.78f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00BCD4).copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.50f, size.height * 0.45f),
                        radius = 270f
                    ),
                    radius = 270f,
                    center = Offset(size.width * 0.50f, size.height * 0.45f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок
            Text(
                stringResource(R.string.create_account_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(R.string.join_worldmates),
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Форма регистрации
            RegisterFormCard(
                username = username,
                onUsernameChange = { username = it },
                email = email,
                onEmailChange = { email = it },
                password = password,
                onPasswordChange = { password = it },
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it },
                passwordVisible = passwordVisible,
                onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                confirmPasswordVisible = confirmPasswordVisible,
                onConfirmPasswordVisibilityToggle = { confirmPasswordVisible = !confirmPasswordVisible },
                phoneNumber = phoneNumber,
                onPhoneNumberChange = { phoneNumber = it },
                selectedGender = selectedGender,
                onGenderChange = { selectedGender = it },
                selectedCountry = selectedCountry,
                onCountryChange = { selectedCountry = it },
                selectedTab = selectedTab,
                onTabChange = { selectedTab = it },
                isLoading = isLoading,
                onRegisterClick = {
                    if (selectedTab == 0) {
                        // Email регистрация
                        if (username.isNotEmpty() && email.isNotEmpty() &&
                            password.isNotEmpty() && password == confirmPassword) {
                            viewModel.registerWithEmail(username, email, password, confirmPassword, selectedGender)
                        }
                    } else {
                        // Phone регистрация - передаємо повний номер з кодом країни
                        if (username.isNotEmpty() && phoneNumber.isNotEmpty() &&
                            password.isNotEmpty() && password == confirmPassword) {
                            val fullPhone = com.worldmates.messenger.ui.components.getFullPhoneNumber(
                                selectedCountry.dialCode, phoneNumber
                            )
                            viewModel.registerWithPhone(username, fullPhone, password, confirmPassword, selectedGender)
                        }
                    }
                },
                registerState = registerState
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопка назад
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.already_have_account) + " ",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
                TextButton(
                    onClick = onBackToLogin
                ) {
                    Text(
                        stringResource(R.string.sign_in),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun premiumFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(alpha = 0.85f),
    disabledTextColor = Color.White.copy(alpha = 0.45f),
    focusedBorderColor = Color(0xFF4FC3F7),
    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
    focusedLabelColor = Color(0xFF4FC3F7),
    unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
    cursorColor = Color(0xFF4FC3F7),
    focusedLeadingIconColor = Color(0xFF4FC3F7),
    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.45f),
    focusedTrailingIconColor = Color(0xFF4FC3F7),
    unfocusedTrailingIconColor = Color.White.copy(alpha = 0.45f),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent
)

@Composable
fun RegisterFormCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    confirmPasswordVisible: Boolean,
    onConfirmPasswordVisibilityToggle: () -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    selectedGender: String,
    onGenderChange: (String) -> Unit,
    selectedCountry: com.worldmates.messenger.ui.components.Country,
    onCountryChange: (com.worldmates.messenger.ui.components.Country) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    isLoading: Boolean,
    onRegisterClick: () -> Unit,
    registerState: RegisterState
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color(0xFF1565C0).copy(alpha = 0.20f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1530).copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Табы для выбора метода регистрации
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF4FC3F7),
                indicator = { tabPositions ->
                    if (tabPositions.isNotEmpty() && selectedTab < tabPositions.size) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedTab])
                                .height(3.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF1565C0), Color(0xFF9C27B0))
                                    ),
                                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                )
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabChange(0) },
                    text = { Text(stringResource(R.string.email)) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabChange(1) },
                    text = { Text(stringResource(R.string.phone)) },
                    icon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Содержимое в зависимости от выбранной вкладки
            when (selectedTab) {
                0 -> {
                    // Регистрация через email + username
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text(stringResource(R.string.username)) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = premiumFieldColors()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !isLoading,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = premiumFieldColors()
                    )
                }
                1 -> {
                    // Регистрация через телефон
                    PhoneInputField(
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = onPhoneNumberChange,
                        selectedCountry = selectedCountry,
                        onCountryChange = onCountryChange,
                        enabled = !isLoading,
                        label = stringResource(R.string.phone_number)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text(stringResource(R.string.username)) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = premiumFieldColors()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Выбор пола
            GenderSelectionGroup(
                selectedGender = selectedGender,
                onGenderChange = onGenderChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = onPasswordVisibilityToggle) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible)
                                stringResource(R.string.hide_password)
                            else
                                stringResource(R.string.show_password)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = premiumFieldColors()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(stringResource(R.string.confirm_password)) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = onConfirmPasswordVisibilityToggle) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible)
                                stringResource(R.string.hide_password)
                            else
                                stringResource(R.string.show_password)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (confirmPasswordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = premiumFieldColors(),
                isError = confirmPassword.isNotEmpty() && password != confirmPassword
            )

            if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.passwords_mismatch),
                    color = Color(0xFFFFCDD2),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Register button
            val registerEnabled = if (selectedTab == 0) {
                username.isNotEmpty() && email.isNotEmpty() &&
                password.isNotEmpty() && password == confirmPassword && !isLoading
            } else {
                phoneNumber.isNotEmpty() && username.isNotEmpty() &&
                password.isNotEmpty() && password == confirmPassword && !isLoading
            }

            GradientButton(
                text = stringResource(R.string.sign_up),
                onClick = onRegisterClick,
                enabled = registerEnabled,
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            // Error message
            if (registerState is RegisterState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF5350).copy(alpha = 0.22f)
                ) {
                    Text(
                        text = registerState.message,
                        color = Color(0xFFFFCDD2),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
