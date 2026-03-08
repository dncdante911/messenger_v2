package com.worldmates.messenger.ui.geo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.repository.LocationRepository
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.ui.messages.MessagesActivity
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.http.GET
import retrofit2.http.Query

// ─── Data model ──────────────────────────────────────────────────────────────

data class NearbyUser(
    @com.google.gson.annotations.SerializedName("user_id") val userId: Long,
    @com.google.gson.annotations.SerializedName("username") val username: String,
    @com.google.gson.annotations.SerializedName("display_name") val displayName: String,
    @com.google.gson.annotations.SerializedName("avatar") val avatar: String? = null,
    @com.google.gson.annotations.SerializedName("distance_km") val distanceKm: Double = 0.0,
    @com.google.gson.annotations.SerializedName("last_seen") val lastSeen: String? = null
)

data class NearbyUsersResponse(
    @com.google.gson.annotations.SerializedName("api_status") val apiStatus: Int,
    @com.google.gson.annotations.SerializedName("users") val users: List<NearbyUser>? = null,
    @com.google.gson.annotations.SerializedName("error_message") val errorMessage: String? = null
)

// ─── Retrofit interface ───────────────────────────────────────────────────────

interface GeoApi {
    @GET("/api/node/users/nearby")
    suspend fun getNearbyUsers(
        @Query("access_token") accessToken: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius_km") radiusKm: Int = 10
    ): NearbyUsersResponse
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class GeoDiscoveryState(
    val isLoading: Boolean = false,
    val users: List<NearbyUser> = emptyList(),
    val error: String? = null,
    val locationGranted: Boolean = false,
    val radiusKm: Int = 10
)

class GeoDiscoveryViewModel : ViewModel() {

    private val _state = MutableStateFlow(GeoDiscoveryState())
    val state: StateFlow<GeoDiscoveryState> = _state.asStateFlow()

    private val geoApi: GeoApi by lazy {
        RetrofitClient.retrofit.create(GeoApi::class.java)
    }

    fun setLocationGranted(granted: Boolean) {
        _state.value = _state.value.copy(locationGranted = granted)
    }

    fun setRadius(radius: Int) {
        _state.value = _state.value.copy(radiusKm = radius)
    }

    fun loadNearbyUsers(lat: Double, lon: Double) {
        val token = UserSession.accessToken ?: run {
            _state.value = _state.value.copy(error = "Not authenticated")
            return
        }
        val radius = _state.value.radiusKm
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = geoApi.getNearbyUsers(
                    accessToken = token,
                    lat = lat,
                    lon = lon,
                    radiusKm = radius
                )
                if (response.apiStatus == 200) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        users = response.users ?: emptyList()
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = response.errorMessage ?: "Failed to load nearby users"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun setError(msg: String) {
        _state.value = _state.value.copy(isLoading = false, error = msg)
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────

class GeoDiscoveryActivity : AppCompatActivity() {

    private lateinit var viewModel: GeoDiscoveryViewModel
    private lateinit var locationRepository: LocationRepository

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initialize(this)
        viewModel = ViewModelProvider(this).get(GeoDiscoveryViewModel::class.java)
        locationRepository = LocationRepository.getInstance(this)

        setContent {
            WorldMatesThemedApp {
                GeoDiscoveryScreen(
                    viewModel = viewModel,
                    locationRepository = locationRepository,
                    onOpenChat = { user ->
                        startActivity(Intent(this, MessagesActivity::class.java).apply {
                            putExtra("recipient_id", user.userId)
                            putExtra("recipient_name", user.displayName)
                            putExtra("recipient_avatar", user.avatar ?: "")
                        })
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoDiscoveryScreen(
    viewModel: GeoDiscoveryViewModel,
    locationRepository: LocationRepository,
    onOpenChat: (NearbyUser) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var showRadiusDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setLocationGranted(granted)
        if (granted) {
            fetchAndLoad(context, locationRepository, viewModel)
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.setLocationGranted(true)
            fetchAndLoad(context, locationRepository, viewModel)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nearby People", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Within ${state.radiusKm} km",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRadiusDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Filter")
                    }
                    IconButton(onClick = {
                        if (state.locationGranted) {
                            fetchAndLoad(context, locationRepository, viewModel)
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                !state.locationGranted -> {
                    GeoPermissionPlaceholder(
                        modifier = Modifier.align(Alignment.Center),
                        onRequestPermission = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    )
                }
                state.error != null -> {
                    GeoErrorPlaceholder(
                        error = state.error!!,
                        modifier = Modifier.align(Alignment.Center),
                        onRetry = { fetchAndLoad(context, locationRepository, viewModel) }
                    )
                }
                state.users.isEmpty() -> {
                    GeoEmptyPlaceholder(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.users, key = { it.userId }) { user ->
                            NearbyUserRow(
                                user = user,
                                onClick = { onOpenChat(user) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRadiusDialog) {
        RadiusPickerDialog(
            current = state.radiusKm,
            onConfirm = { radius ->
                viewModel.setRadius(radius)
                showRadiusDialog = false
                if (state.locationGranted) {
                    fetchAndLoad(context, locationRepository, viewModel)
                }
            },
            onDismiss = { showRadiusDialog = false }
        )
    }
}

// ─── Helper: fetch location then load users ───────────────────────────────────

private fun fetchAndLoad(
    context: android.content.Context,
    locationRepository: LocationRepository,
    viewModel: GeoDiscoveryViewModel
) {
    viewModel.viewModelScope.launch {
        locationRepository.getCurrentLocation()
            .fold(
                onSuccess = { latLng ->
                    viewModel.loadNearbyUsers(latLng.latitude, latLng.longitude)
                },
                onFailure = { e ->
                    viewModel.setError("Location error: ${e.message}")
                }
            )
    }
}

// ─── Composables ─────────────────────────────────────────────────────────────

@Composable
private fun NearbyUserRow(user: NearbyUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            if (user.avatar != null) {
                AsyncImage(
                    model = user.avatar,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF667eea), Color(0xFF764ba2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.displayName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (user.distanceKm < 1) "${(user.distanceKm * 1000).toInt()} m"
                               else "${"%.1f".format(user.distanceKm)} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun GeoPermissionPlaceholder(modifier: Modifier, onRequestPermission: () -> Unit) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Location access needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Allow location access to find people nearby",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRequestPermission) {
            Text("Allow Location")
        }
    }
}

@Composable
private fun GeoEmptyPlaceholder(modifier: Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.PeopleAlt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("No one nearby", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "No users found in this area. Try increasing the search radius.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GeoErrorPlaceholder(error: String, modifier: Modifier, onRetry: () -> Unit) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun RadiusPickerDialog(current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(1, 5, 10, 25, 50)
    var selected by remember { mutableIntStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search radius") },
        text = {
            Column {
                options.forEach { km ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = km }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == km, onClick = { selected = km })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$km km")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
