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
import androidx.compose.foundation.border
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090D1A),
                        Color(0xFF121539),
                        Color(0xFF0A0F24)
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    color = Color(0xFF1565C0).copy(alpha = 0.22f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.85f, size.height * 0.12f)
                )
                drawCircle(
                    color = Color(0xFF6A1B9A).copy(alpha = 0.18f),
                    radius = size.width * 0.50f,
                    center = Offset(size.width * 0.10f, size.height * 0.72f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
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

    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color.Black.copy(alpha = 0.30f)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Табы для выбора метода регистрации
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = colorScheme.primary,
                indicator = { tabPositions ->
                    if (tabPositions.isNotEmpty() && selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = colorScheme.primary
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            focusedLabelColor = colorScheme.primary,
                            cursorColor = colorScheme.primary
                        )
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            focusedLabelColor = colorScheme.primary,
                            cursorColor = colorScheme.primary
                        )
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            focusedLabelColor = colorScheme.primary,
                            cursorColor = colorScheme.primary
                        )
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    focusedLabelColor = colorScheme.primary,
                    cursorColor = colorScheme.primary
                )
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    focusedLabelColor = colorScheme.primary,
                    cursorColor = colorScheme.primary
                ),
                isError = confirmPassword.isNotEmpty() && password != confirmPassword
            )

            if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.passwords_mismatch),
                    color = colorScheme.error,
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
                    color = colorScheme.error.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = registerState.message,
                        color = colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
