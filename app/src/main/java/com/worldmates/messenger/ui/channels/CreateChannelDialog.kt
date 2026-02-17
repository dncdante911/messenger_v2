package com.worldmates.messenger.ui.channels

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CreateChannelDialog(
    viewModel: ChannelsViewModel = viewModel(),
    onDismiss: () -> Unit,
    onSuccess: (com.worldmates.messenger.data.model.Channel) -> Unit
) {
    var channelName by remember { mutableStateOf("") }
    var channelUsername by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isCreating by viewModel.isCreatingChannel.collectAsState()

    Dialog(onDismissRequest = { if (!isCreating) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Створити канал",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    if (!isCreating) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Закрити", tint = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Channel Avatar (placeholder)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                            )
                        )
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Channel Name
                OutlinedTextField(
                    value = channelName,
                    onValueChange = {
                        channelName = it
                        errorMessage = null
                    },
                    label = { Text("Назва каналу *") },
                    placeholder = { Text("Мій канал") },
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF667eea),
                        focusedLabelColor = Color(0xFF667eea)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Channel Username
                OutlinedTextField(
                    value = channelUsername,
                    onValueChange = {
                        // Only allow alphanumeric and underscores
                        if (it.all { char -> char.isLetterOrDigit() || char == '_' }) {
                            channelUsername = it.lowercase()
                        }
                        errorMessage = null
                    },
                    label = { Text("@username (опціонально)") },
                    placeholder = { Text("mychannel") },
                    singleLine = true,
                    enabled = !isCreating,
                    leadingIcon = { Text("@", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF667eea),
                        focusedLabelColor = Color(0xFF667eea)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Channel Description
                OutlinedTextField(
                    value = channelDescription,
                    onValueChange = {
                        channelDescription = it
                        errorMessage = null
                    },
                    label = { Text("Опис (опціонально)") },
                    placeholder = { Text("Розкажіть про ваш канал...") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF667eea),
                        focusedLabelColor = Color(0xFF667eea)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Private Channel Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable(enabled = !isCreating) { isPrivate = !isPrivate }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                            tint = Color(0xFF667eea),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                if (isPrivate) "Приватний канал" else "Публічний канал",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C3E50)
                            )
                            Text(
                                if (isPrivate) "Тільки за запрошенням" else "Будь-хто може підписатись",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        enabled = !isCreating,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF667eea),
                            checkedTrackColor = Color(0xFF667eea).copy(alpha = 0.5f)
                        )
                    )
                }

                // Error Message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                errorMessage ?: "",
                                fontSize = 13.sp,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isCreating
                    ) {
                        Text("Скасувати", color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            viewModel.createChannel(
                                name = channelName,
                                username = channelUsername.ifBlank { null },
                                description = channelDescription.ifBlank { null },
                                isPrivate = isPrivate,
                                onSuccess = { channel ->
                                    onSuccess(channel)
                                },
                                onError = { error ->
                                    errorMessage = error
                                }
                            )
                        },
                        enabled = !isCreating && channelName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF667eea),
                            disabledContainerColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (isCreating) "Створення..." else "Створити",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
