package com.worldmates.messenger.ui.business

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.BusinessDirectoryItem
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.ui.messages.MessagesActivity
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────────────

class BusinessProfileViewViewModel : ViewModel() {

    private val _profile  = MutableStateFlow<BusinessDirectoryItem?>(null)
    val profile: StateFlow<BusinessDirectoryItem?> = _profile

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load(userId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val resp = NodeRetrofitClient.businessDirectoryApi.getBusinessProfile(userId)
                if (resp.isSuccessful) {
                    _profile.value = resp.body()
                } else {
                    _error.value = "HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────

class BusinessProfileViewActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val userId = intent.getLongExtra("user_id", 0L)

        val viewModel = ViewModelProvider(this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return BusinessProfileViewViewModel() as T
                }
            }
        ).get(BusinessProfileViewViewModel::class.java)

        viewModel.load(userId)

        setContent {
            WorldMatesThemedApp {
                BusinessProfileViewScreen(
                    viewModel = viewModel,
                    onBack    = { finish() },
                    onChat    = { uid, name, avatarUrl ->
                        val intent = Intent(this, MessagesActivity::class.java)
                        intent.putExtra("recipient_id", uid)
                        intent.putExtra("recipient_name", name)
                        intent.putExtra("recipient_avatar", avatarUrl ?: "")
                        intent.putExtra("is_business_chat", true)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val ProfDeep   = Color(0xFF0D1B2A)
private val ProfDark   = Color(0xFF1A2942)
private val ProfMid    = Color(0xFF243B55)
private val ProfAccent = Color(0xFF1E90FF)
private val ProfGold   = Color(0xFFFFD166)
private val ProfCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileViewScreen(
    viewModel: BusinessProfileViewViewModel,
    onBack: () -> Unit,
    onChat: (Long, String, String?) -> Unit
) {
    val profile   by viewModel.profile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(ProfDeep)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text       = profile?.businessName ?: "",
                            color      = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 18.sp,
                            maxLines   = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ProfDark)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = ProfAccent
                    )
                    error != null -> Text(
                        text     = error ?: "",
                        color    = Color.Red,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                    profile != null -> ProfileContent(
                        item   = profile!!,
                        onChat = { uid, name, av -> onChat(uid, name, av) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    item: BusinessDirectoryItem,
    onChat: (Long, String, String?) -> Unit
) {
    Column(
        modifier              = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(16.dp)
    ) {
        // ── Avatar ────────────────────────────────────────────────────────────
        if (!item.avatar.isNullOrBlank()) {
            AsyncImage(
                model             = item.avatar,
                contentDescription = item.businessName,
                contentScale      = ContentScale.Crop,
                modifier          = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(ProfMid)
            )
        } else {
            val initials = item.businessName.split(" ").take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "?" }
            Box(
                modifier         = Modifier.size(100.dp).clip(CircleShape).background(ProfAccent),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            }
        }

        // ── Name + badge ──────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(item.businessName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (item.isVerified) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Verified, null, tint = ProfAccent, modifier = Modifier.size(20.dp))
            }
        }

        // ── Category chip ─────────────────────────────────────────────────────
        Surface(shape = RoundedCornerShape(50), color = ProfMid) {
            Text(item.category, color = ProfGold, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
        }

        // ── Chat button ───────────────────────────────────────────────────────
        Button(
            onClick = { onChat(item.userId, item.businessName, item.avatar) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProfAccent)
        ) {
            Icon(Icons.Default.Message, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Написати", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        // ── Details card ──────────────────────────────────────────────────────
        Surface(shape = RoundedCornerShape(14.dp), color = ProfCard, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                item.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(desc, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                        lineHeight = 20.sp)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }

                item.address?.takeIf { it.isNotBlank() }?.let { addr ->
                    InfoRow(icon = Icons.Default.LocationOn, text = addr)
                }

                item.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    InfoRow(icon = Icons.Default.Phone, text = phone)
                }

                item.email?.takeIf { it.isNotBlank() }?.let { email ->
                    InfoRow(icon = Icons.Default.Email, text = email)
                }

                item.website?.takeIf { it.isNotBlank() }?.let { site ->
                    InfoRow(icon = Icons.Default.Language, text = site, textColor = ProfAccent)
                }

                item.distance?.let { dist ->
                    InfoRow(icon = Icons.Default.NearMe, text = "$dist km від вас",
                        textColor = Color.White.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    text: String,
    textColor: Color = Color.White.copy(alpha = 0.8f)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ProfAccent.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = textColor, fontSize = 14.sp)
    }
}
