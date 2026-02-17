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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.ui.chats.ChatsActivity
import com.worldmates.messenger.ui.components.*
import com.worldmates.messenger.ui.theme.*
import kotlinx.coroutines.launch

class QuickRegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            WorldMatesThemedApp {
                QuickRegisterScreen(
                    onBack = { finish() },
                    onSuccess = {
                        startActivity(Intent(this, ChatsActivity::class.java))
                        finishAffinity()
                    }
                )
            }
        }
    }
}

enum class QuickRegStep {
    ENTER_CONTACT,
    ENTER_CODE
}

@Composable
fun QuickRegisterScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(QuickRegStep.ENTER_CONTACT) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=email, 1=phone
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(popularCountries[0]) }
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var userId by remember { mutableLongStateOf(0L) }
    var username by remember { mutableStateOf("") }

    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

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
                    colors = listOf(GradientStart, GradientEnd, WMPrimary),
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
            // Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                }
                Text(
                    "Швидка реєстрація",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info text
            Text(
                "Введіть email або телефон — ми надішлемо код підтвердження.\nНіяких логінів та паролів!",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    when (step) {
                        QuickRegStep.ENTER_CONTACT -> {
                            Text(
                                "Спосіб реєстрації",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color.Transparent,
                                contentColor = colorScheme.primary
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("Email") },
                                    icon = { Icon(Icons.Default.Email, null, Modifier.size(20.dp)) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("Телефон") },
                                    icon = { Icon(Icons.Default.Phone, null, Modifier.size(20.dp)) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            when (selectedTab) {
                                0 -> {
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text("Email") },
                                        leadingIcon = { Icon(Icons.Default.Email, null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        enabled = !isLoading,
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                1 -> {
                                    PhoneInputField(
                                        phoneNumber = phoneNumber,
                                        onPhoneNumberChange = { phoneNumber = it },
                                        selectedCountry = selectedCountry,
                                        onCountryChange = { selectedCountry = it },
                                        enabled = !isLoading,
                                        label = "Номер телефону"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            GradientButton(
                                text = "Отримати код",
                                onClick = {
                                    errorMessage = null
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val response = RetrofitClient.apiService.quickRegister(
                                                email = if (selectedTab == 0) email else null,
                                                phoneNumber = if (selectedTab == 1)
                                                    getFullPhoneNumber(selectedCountry.dialCode, phoneNumber)
                                                else null
                                            )

                                            if (response.apiStatus == 200) {
                                                userId = response.userId ?: 0
                                                username = response.username ?: ""
                                                successMessage = response.message
                                                step = QuickRegStep.ENTER_CODE
                                            } else {
                                                errorMessage = response.errorMessage ?: "Помилка реєстрації"
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

                        QuickRegStep.ENTER_CODE -> {
                            Text(
                                "Введіть код",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                successMessage ?: "Код надіслано",
                                fontSize = 14.sp,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = code,
                                onValueChange = { if (it.length <= 6) code = it },
                                label = { Text("6-значний код") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !isLoading,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            GradientButton(
                                text = "Підтвердити",
                                onClick = {
                                    errorMessage = null
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val contact = if (selectedTab == 0) email
                                                else getFullPhoneNumber(selectedCountry.dialCode, phoneNumber)

                                            val response = RetrofitClient.apiService.quickVerify(
                                                email = if (selectedTab == 0) contact else null,
                                                phoneNumber = if (selectedTab == 1) contact else null,
                                                code = code
                                            )

                                            if (response.apiStatus == 200 && response.accessToken != null) {
                                                // Save session and navigate
                                                UserSession.saveSession(
                                                    token = response.accessToken,
                                                    id = response.userId ?: 0,
                                                    username = response.username
                                                )
                                                onSuccess()
                                            } else {
                                                errorMessage = response.errorMessage ?: "Невірний код"
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Помилка мережі: ${e.localizedMessage}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                enabled = code.length == 6 && !isLoading,
                                isLoading = isLoading,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Resend button
                            TextButton(onClick = {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val response = RetrofitClient.apiService.quickRegister(
                                            email = if (selectedTab == 0) email else null,
                                            phoneNumber = if (selectedTab == 1)
                                                getFullPhoneNumber(selectedCountry.dialCode, phoneNumber)
                                            else null
                                        )
                                        if (response.apiStatus == 200) {
                                            successMessage = "Код надіслано повторно"
                                        } else {
                                            errorMessage = response.errorMessage
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Помилка: ${e.localizedMessage}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }) {
                                Text("Надіслати код повторно")
                            }

                            TextButton(onClick = {
                                step = QuickRegStep.ENTER_CONTACT
                                code = ""
                                errorMessage = null
                            }) {
                                Text("Повернутись назад")
                            }
                        }
                    }

                    // Error message
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.error.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
