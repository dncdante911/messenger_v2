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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.worldmates.messenger.R
import com.worldmates.messenger.data.AccountManager
import com.worldmates.messenger.ui.chats.ChatsActivity
import com.worldmates.messenger.ui.components.*
import com.worldmates.messenger.ui.language.LanguageSelectionActivity
import com.worldmates.messenger.ui.theme.*
import com.worldmates.messenger.utils.LanguageManager
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class LoginActivity : AppCompatActivity() {

    companion object {
        /** Pass this extra (Boolean = true) when opening LoginActivity to add a second account. */
        const val EXTRA_ADD_ACCOUNT = "add_account_mode"
    }

    private lateinit var viewModel: LoginViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Дозволяємо Compose керувати window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val isAddAccountMode = intent.getBooleanExtra(EXTRA_ADD_ACCOUNT, false)

        // Перший запуск: показати вибір мови (тільки при звичайному логіні)
        if (!isAddAccountMode && !LanguageManager.isLanguageSelected) {
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
            finish()
            return
        }

        // Авто-логін: пропускаємо в режимі add-account (там вже залогований)
        if (!isAddAccountMode && com.worldmates.messenger.data.UserSession.isLoggedIn) {
            navigateToChats()
            return
        }

        viewModel = ViewModelProvider(this).get(LoginViewModel::class.java)
        viewModel.isAddAccountMode = isAddAccountMode

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        setContent {
            WorldMatesThemedApp {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = { navigateToChats() },
                    onNavigateToRegister = { navigateToRegister() },
                    onNavigateToForgotPassword = { navigateToForgotPassword() },
                    onNavigateToQuickRegister = { navigateToQuickRegister() },
                    onLanguageToggle = {
                        val newLang = if (LanguageManager.currentLanguage == LanguageManager.LANG_UK)
                            LanguageManager.LANG_RU else LanguageManager.LANG_UK
                        LanguageManager.setLanguage(newLang)
                        recreate()
                    }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Success -> {
                        // Normal login: save session as account, then go to chats
                        lifecycleScope.launch {
                            AccountManager.saveCurrentSessionAsAccount()
                        }
                        Toast.makeText(
                            this@LoginActivity,
                            getString(R.string.login_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToChats()
                    }
                    is LoginState.AddAccountSuccess -> {
                        // Add-account mode: persist new account, switch to it, then return to chats.
                        // All DB work happens inside the coroutine so finish() is only called after
                        // everything is complete — no race with lifecycleScope cancellation.
                        lifecycleScope.launch {
                            val added = AccountManager.addOrUpdateAccount(
                                userId   = state.userId,
                                token    = state.token,
                                username = state.username,
                                avatar   = state.avatar,
                                isPro    = 0  // will sync on next profile refresh
                            )
                            if (added) {
                                AccountManager.switchAccount(state.userId)
                            }
                            Toast.makeText(
                                this@LoginActivity,
                                getString(R.string.account_added),
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()  // go back to ChatsActivity — account already switched
                        }
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
    onNavigateToQuickRegister: () -> Unit = {},
    onLanguageToggle: () -> Unit = {}
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Language toggle button — top right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LanguageToggleChip(onToggle = onLanguageToggle)
            }

            Spacer(modifier = Modifier.height(20.dp))

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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val logoTransition = rememberInfiniteTransition(label = "logo")
        val pulseScale by logoTransition.animateFloat(
            initialValue = 1f, targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse"
        )
        val ringAlpha by logoTransition.animateFloat(
            initialValue = 0.55f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
            label = "ringAlpha"
        )
        val ringScale by logoTransition.animateFloat(
            initialValue = 1f, targetValue = 1.65f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
            label = "ringScale"
        )

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp)) {
            // Pulsing halo ring
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .scale(ringScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E88E5).copy(alpha = ringAlpha), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )
            // Logo disc
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .scale(pulseScale)
                    .shadow(
                        elevation = 32.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF1565C0).copy(alpha = 0.60f),
                        spotColor = Color(0xFF9C27B0).copy(alpha = 0.40f)
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF1565C0), Color(0xFF7B1FA2), Color(0xFF00BCD4))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("WM", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "WallyMates Messenger",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.login_world_subtitle),
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color(0xFF1565C0).copy(alpha = 0.22f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1530).copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                stringResource(R.string.login),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Табы для переключения
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
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.login_email_tab)) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.phone)) },
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
                        label = { Text(stringResource(R.string.username_or_email)) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !isLoading,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = premiumFieldColors()
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
                }

                1 -> {
                    // Логин через телефон
                    PhoneInputField(
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = { phoneNumber = it },
                        selectedCountry = selectedCountry,
                        onCountryChange = { selectedCountry = it },
                        enabled = !isLoading,
                        label = stringResource(R.string.phone_number)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password field для телефона
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
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Forgot password / recovery
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    stringResource(R.string.recovery_access_btn),
                    color = Color(0xFF4FC3F7),
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
                text = stringResource(R.string.sign_in),
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
                    color = Color(0xFFEF5350).copy(alpha = 0.22f)
                ) {
                    Text(
                        text = loginState.message,
                        color = Color(0xFFFFCDD2),
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
            stringResource(R.string.no_account_question),
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
            Text(stringResource(R.string.register), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                    stringResource(R.string.reg_type_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.reg_account_setup_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                )
                Spacer(modifier = Modifier.height(20.dp))

                RegOptionCard(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.standard_register),
                    desc = stringResource(R.string.std_reg_desc),
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
                    title = stringResource(R.string.quick_register),
                    desc = stringResource(R.string.quick_reg_desc),
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

@Composable
fun LanguageToggleChip(onToggle: () -> Unit) {
    val currentLang = LanguageManager.currentLanguage
    val (flag, code) = if (currentLang == LanguageManager.LANG_UK) "🇺🇦" to "UA" else "🇷🇺" to "RU"

    Surface(
        onClick = onToggle,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.White.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(flag, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                code,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
