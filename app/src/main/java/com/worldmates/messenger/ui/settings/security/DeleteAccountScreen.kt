package com.worldmates.messenger.ui.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    onBackClick: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val password = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val showConfirmDialog = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFc0392b), Color(0xFF8e0000))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.delete_account), fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )

                Text(
                    text = stringResource(R.string.delete_account_warning_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            R.string.delete_account_consequence_1,
                            R.string.delete_account_consequence_2,
                            R.string.delete_account_consequence_3
                        ).forEach { res ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•  ", color = Color.White, fontSize = 14.sp)
                                Text(stringResource(res), color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = password.value,
                    onValueChange = {
                        password.value = it
                        errorMessage.value = null
                    },
                    label = { Text(stringResource(R.string.password_hint), color = Color.White.copy(alpha = 0.7f)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = errorMessage.value != null,
                    supportingText = errorMessage.value?.let { { Text(it, color = Color(0xFFFFCDD2)) } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White
                    ),
                    singleLine = true
                )

                Button(
                    onClick = { showConfirmDialog.value = true },
                    enabled = password.value.isNotBlank() && !isLoading.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isLoading.value) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color(0xFFc0392b), strokeWidth = 2.dp)
                    } else {
                        Text(
                            stringResource(R.string.delete_account_btn),
                            color = Color(0xFFc0392b),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (showConfirmDialog.value) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog.value = false },
            title = { Text(stringResource(R.string.delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.delete_account_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog.value = false
                        scope.launch {
                            isLoading.value = true
                            try {
                                val token = UserSession.accessToken ?: ""
                                val response = RetrofitClient.apiService.deleteAccount(
                                    accessToken = token,
                                    password = password.value
                                )
                                if (response.apiStatus == 200) {
                                    onAccountDeleted()
                                } else {
                                    errorMessage.value = response.errorMessage ?: response.message
                                }
                            } catch (e: Exception) {
                                errorMessage.value = e.message
                            } finally {
                                isLoading.value = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete_account), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
