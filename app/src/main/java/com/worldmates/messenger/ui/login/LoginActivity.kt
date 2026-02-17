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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.worldmates.messenger.ui.chats.ChatsActivity
import com.worldmates.messenger.ui.components.*
import com.worldmates.messenger.ui.theme.*
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Дозволяємо Compose керувати window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Проверка автологина
        if (com.worldmates.messenger.data.UserSession.isLoggedIn) {
            navigateToChats()
            return
        }

        viewModel = ViewModelProvider(this).get(LoginViewModel::class.java)

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        setContent {
            WorldMatesThemedApp {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = { navigateToChats() },
                    onNavigateToRegister = { navigateToRegister() }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Success -> {
                        Toast.makeText(
                            this@LoginActivity,
                            "Успішно увійшли!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToChats()
                    }
                    is LoginState.Error -> {
                        Toast.makeText(
                            this@LoginActivity,
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

    private fun navigateToRegister() {
        startActivity(Intent(this, com.worldmates.messenger.ui.register.RegisterActivity::class.java))
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val loginState by viewModel.loginState.collectAsState()
    val isLoading = loginState is LoginState.Loading

    // Анимация появления элементов
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000)) +
                        slideInVertically(
                            initialOffsetY = { -100 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
            ) {
                LogoSection()
            }

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 200)) +
                        slideInVertically(
                            initialOffsetY = { 100 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
            ) {
                LoginFormCard(
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    passwordVisible = passwordVisible,
                    onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                    isLoading = isLoading,
                    onLoginClick = {
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            viewModel.login(username, password)
                        }
                    },
                    loginState = loginState
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 400))
            ) {
                RegisterPrompt(
                    onNavigateToRegister = onNavigateToRegister
                )
            }
        }
    }
}

@Composable
fun LogoSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Анимированный логотип
        val scale by rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Surface(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color.Black.copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = Color.White
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    "WM",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = WMPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "WorldMates Messenger",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Спілкуйтесь з друзями по всьому світу",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun LoginFormCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    isLoading: Boolean,
    onLoginClick: () -> Unit,
    loginState: LoginState
) {
    var selectedTab by remember { mutableStateOf(0) }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(popularCountries[0]) }  // Ukraine by default

    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "Вхід",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Табы для переключения
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
                    onClick = { selectedTab = 0 },
                    text = { Text("Логін/Email") },
                    icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Телефон") },
                    icon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Содержимое в зависимости от выбранной вкладки
            when (selectedTab) {
                0 -> {
                    // Логин через username/email
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("Ім'я користувача або email") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Пароль") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = onPasswordVisibilityToggle) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Сховати пароль" else "Показати пароль"
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
                }

                1 -> {
                    // Логин через телефон
                    PhoneInputField(
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = { phoneNumber = it },
                        selectedCountry = selectedCountry,
                        onCountryChange = { selectedCountry = it },
                        enabled = !isLoading,
                        label = "Номер телефону"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password field для телефона
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Пароль") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = onPasswordVisibilityToggle) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Сховати пароль" else "Показати пароль"
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
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Forgot password
            TextButton(
                onClick = { /* TODO */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    "Забули пароль?",
                    color = colorScheme.primary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Login button
            val loginEnabled = if (selectedTab == 0) {
                username.isNotEmpty() && password.isNotEmpty() && !isLoading
            } else {
                phoneNumber.isNotEmpty() && password.isNotEmpty() && !isLoading
            }

            GradientButton(
                text = "Увійти",
                onClick = {
                    if (selectedTab == 0) {
                        onLoginClick()
                    } else {
                        // Логин через телефон
                        val fullPhone = getFullPhoneNumber(selectedCountry.dialCode, phoneNumber)
                        onUsernameChange(fullPhone)  // Передаем телефон как username
                        onLoginClick()
                    }
                },
                enabled = loginEnabled,
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            // Error message
            if (loginState is LoginState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.error.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = loginState.message,
                        color = colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterPrompt(onNavigateToRegister: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Немаєте облікового запису? ",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp
        )
        TextButton(
            onClick = onNavigateToRegister
        ) {
            Text(
                "Зареєструйтеся",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
