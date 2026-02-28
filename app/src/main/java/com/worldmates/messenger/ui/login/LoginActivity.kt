package com.worldmates.messenger.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.worldmates.messenger.ui.language.LanguageSelectionActivity
import com.worldmates.messenger.ui.theme.*
import com.worldmates.messenger.utils.LanguageManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: LoginViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Дозволяємо Compose керувати window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Перший запуск: показати вибір мови
        if (!LanguageManager.isLanguageSelected) {
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
            finish()
            return
        }

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
                    onNavigateToRegister = { navigateToRegister() },
                    onNavigateToForgotPassword = { navigateToForgotPassword() },
                    onNavigateToQuickRegister = { navigateToQuickRegister() }
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
        val dest = if (!com.worldmates.messenger.ui.preferences.UIStylePreferences.hasSeenOnboarding(this)) {
            // First login — show UI-style picker before the main screen
            Intent(this, com.worldmates.messenger.ui.onboarding.UIStyleOnboardingActivity::class.java)
        } else {
            Intent(this, ChatsActivity::class.java)
        }
        startActivity(dest)
        finish()
    }

    private fun navigateToRegister() {
        startActivity(Intent(this, com.worldmates.messenger.ui.register.RegisterActivity::class.java))
    }

    private fun navigateToForgotPassword() {
        startActivity(Intent(this, ForgotPasswordActivity::class.java))
    }

    private fun navigateToQuickRegister() {
        startActivity(Intent(this, QuickRegisterActivity::class.java))
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    onNavigateToQuickRegister: () -> Unit = {}
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

    // Deep dark premium background colors
    val bgStart = Color(0xFF090D1A)   // deep night navy
    val bgMid   = Color(0xFF121539)   // deep indigo
    val bgEnd   = Color(0xFF0A0F24)   // dark slate

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(bgStart, bgMid, bgEnd),
                    startY = gradientOffset * 0.05f,
                    endY   = gradientOffset * 0.05f + 2000f
                )
            )
            // Decorative radial glow blobs — purely visual, no extra imports needed
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1565C0).copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.08f),
                        radius = 320f
                    ),
                    radius = 320f,
                    center = Offset(size.width * 0.85f, size.height * 0.08f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF6A1B9A).copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.72f),
                        radius = 280f
                    ),
                    radius = 280f,
                    center = Offset(size.width * 0.12f, size.height * 0.72f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

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
                    onPhoneLoginClick = { phone ->
                        // Phone login: pass phone number directly without going through
                        // username state (Compose state update is async — using it
                        // in the same click handler would send the old username value)
                        if (phone.isNotEmpty() && password.isNotEmpty()) {
                            viewModel.login(phone, password)
                        }
                    },
                    loginState = loginState,
                    onForgotPassword = onNavigateToForgotPassword
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 400))
            ) {
                RegisterPrompt(
                    onNavigateToRegister = onNavigateToRegister,
                    onNavigateToQuickRegister = onNavigateToQuickRegister
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
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

        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(scale)
                .shadow(
                    elevation = 28.dp,
                    shape = CircleShape,
                    ambientColor = WMPrimary.copy(alpha = 0.55f),
                    spotColor = WMPrimary.copy(alpha = 0.35f)
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1565C0), Color(0xFF6A1B9A))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "WM",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
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
    onPhoneLoginClick: (String) -> Unit = {},
    loginState: LoginState,
    onForgotPassword: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(popularCountries[0]) }  // Ukraine by default

    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color(0xFF1565C0).copy(alpha = 0.18f)
            ),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = 0.06f)
        )
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

            // Forgot password / recovery
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    "Відновлення доступу",
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
                        // Phone login: build the phone string here and pass it directly.
                        // Do NOT call onUsernameChange first — Compose state updates are
                        // deferred to the next recomposition, so onLoginClick() would still
                        // read the old username value from the email tab.
                        val fullPhone = getFullPhoneNumber(selectedCountry.dialCode, phoneNumber)
                        onPhoneLoginClick(fullPhone)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterPrompt(
    onNavigateToRegister: () -> Unit,
    onNavigateToQuickRegister: () -> Unit = {}
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Немаєте облікового запису?",
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = { showSheet = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White.copy(alpha = 0.09f),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Реєстрація", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    "Оберіть тип реєстрації",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Ви зможете налаштувати акаунт після входу",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                )
                Spacer(modifier = Modifier.height(20.dp))

                RegOptionCard(
                    icon = Icons.Default.Person,
                    title = "Стандартна реєстрація",
                    desc = "Ім'я, email або телефон, пароль — повний контроль над акаунтом",
                    gradient = listOf(Color(0xFF1565C0), Color(0xFF1E88E5)),
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showSheet = false
                            onNavigateToRegister()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                RegOptionCard(
                    icon = Icons.Default.Email,
                    title = "Швидка реєстрація",
                    desc = "Лише email або телефон — вхід через код підтвердження, без паролю",
                    gradient = listOf(Color(0xFF6A1B9A), Color(0xFF8E24AA)),
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showSheet = false
                            onNavigateToQuickRegister()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RegOptionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Brush.linearGradient(gradient), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(3.dp))
                Text(desc, fontSize = 12.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                modifier = Modifier.size(20.dp))
        }
    }
}
