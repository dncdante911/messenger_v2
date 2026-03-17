package com.worldmates.messenger.ui.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.NodeSessionItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(onBackClick: () -> Unit) {
    val sessions = remember { mutableStateListOf<NodeSessionItem>() }
    val isLoading = remember { mutableStateOf(true) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val response = NodeRetrofitClient.api.getNodeSessions()
                if (response.apiStatus == 200) {
                    sessions.clear()
                    sessions.addAll(response.sessions)
                } else {
                    errorMessage.value = response.errorMessage ?: "Error"
                }
            } catch (e: Exception) {
                errorMessage.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.active_sessions), fontSize = 18.sp) },
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

            when {
                isLoading.value -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                errorMessage.value != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage.value ?: "", color = Color.White)
                    }
                }
                sessions.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_active_sessions), color = Color.White)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                onTerminate = {
                                    scope.launch {
                                        try {
                                            val resp = NodeRetrofitClient.api.deleteNodeSession(session.id)
                                            if (resp.apiStatus == 200) {
                                                sessions.remove(session)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: NodeSessionItem, onTerminate: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedTime = remember(session.time) {
        if (session.time > 0) dateFormat.format(Date(session.time * 1000)) else "-"
    }

    val platformIcon = when {
        session.platform.contains("web", ignoreCase = true) -> Icons.Default.Language
        else -> Icons.Default.Phone
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(platformIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.platform.ifBlank { "-" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (!session.ipAddress.isNullOrBlank()) {
                    Text(text = session.ipAddress, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Text(text = formattedTime, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            IconButton(onClick = onTerminate) {
                Icon(Icons.Default.Close, stringResource(R.string.terminate_session), tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
