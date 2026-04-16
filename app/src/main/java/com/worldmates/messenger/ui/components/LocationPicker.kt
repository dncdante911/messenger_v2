package com.worldmates.messenger.ui.components

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.data.repository.LocationData
import com.worldmates.messenger.data.repository.LocationRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Режимы LocationPicker
 */
private enum class LocationPickerMode {
    PICK,   // Выбор места на карте
    LIVE    // Live Location (текущее местоположение)
}

/**
 * 📍 LocationPicker - выбор геолокации на карте
 *
 * Режимы:
 * - PICK: Выбрать место на карте (перемещение карты)
 * - CURRENT: Отправить текущее местоположение (Live Location)
 *
 * Использование:
 * ```
 * var showLocationPicker by remember { mutableStateOf(false) }
 *
 * if (showLocationPicker) {
 *     LocationPicker(
 *         onLocationSelected = { locationData ->
 *             viewModel.sendLocation(locationData)
 *         },
 *         onDismiss = { showLocationPicker = false }
 *     )
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LocationPicker(
    onLocationSelected: (LocationData) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialLocation: LatLng? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationRepo = remember { LocationRepository.getInstance(context) }

    // State
    var selectedLocation by remember { mutableStateOf(initialLocation) }
    var address by remember { mutableStateOf("") }
    var isLoadingAddress by remember { mutableStateOf(false) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var pickerMode by remember { mutableStateOf(LocationPickerMode.PICK) }

    // Використовуємо останню відому локацію з репозиторію як дефолт (замість хардкоду Києва)
    val lastKnownLocation = remember {
        locationRepo.currentLocation.value?.let { loc ->
            LatLng(loc.latitude, loc.longitude)
        }
    }
    val defaultLocation = lastKnownLocation ?: LatLng(50.4501, 30.5234) // fallback — Київ

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            selectedLocation ?: defaultLocation,
            15f
        )
    }

    // Location permissions
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Получить текущую геолокацию при открытии
    LaunchedEffect(Unit) {
        if (locationPermissions.allPermissionsGranted && selectedLocation == null) {
            isLoadingLocation = true
            scope.launch {
                locationRepo.getCurrentLocation().onSuccess { latLng ->
                    selectedLocation = latLng
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
                isLoadingLocation = false
            }
        }
    }

    // Оновлювати адресу при зупинці камери (в режимі PICK) з дебаунсом 400мс.
    // Без дебаунсу запит геокодування виконується при кожній зупинці (навіть проміжній),
    // що призводить до зайвих мережевих запитів.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving && pickerMode == LocationPickerMode.PICK) {
            // Дебаунс: чекаємо 400мс перед геокодуванням
            delay(400)
            // Перевіряємо що камера все ще стоїть (користувач не почав рухати знову)
            if (!cameraPositionState.isMoving) {
                val centerLatLng = cameraPositionState.position.target
                selectedLocation = centerLatLng

                isLoadingAddress = true
                scope.launch {
                    locationRepo.getAddressFromLocation(centerLatLng).onSuccess { addr ->
                        address = addr
                    }
                    isLoadingAddress = false
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(600.dp),
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Проверка разрешений
                    if (!locationPermissions.allPermissionsGranted) {
                        // Запрос разрешений
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📍",
                                fontSize = 64.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.contacts_permission_required),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.allow_location_access),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { locationPermissions.launchMultiplePermissionRequest() }
                            ) {
                                Text(stringResource(R.string.allow_access))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    } else {
                        // Google Map
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                isMyLocationEnabled = true,
                                mapType = MapType.NORMAL
                            ),
                            uiSettings = MapUiSettings(
                                myLocationButtonEnabled = false,
                                zoomControlsEnabled = false,
                                compassEnabled = true,
                                mapToolbarEnabled = false
                            )
                        ) {
                            // Маркер в режиме Live Location
                            if (pickerMode == LocationPickerMode.LIVE && selectedLocation != null) {
                                Marker(
                                    state = MarkerState(position = selectedLocation!!),
                                    title = stringResource(R.string.location_your_location)
                                )
                            }
                        }

                        // Центральный pin в режиме PICK
                        if (pickerMode == LocationPickerMode.PICK) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📍",
                                    fontSize = 48.sp,
                                    modifier = Modifier.offset(y = (-24).dp)
                                )
                            }
                        }

                        // Кнопка "Моя геолокация"
                        FloatingActionButton(
                            onClick = {
                                if (locationPermissions.allPermissionsGranted) {
                                    isLoadingLocation = true
                                    scope.launch {
                                        locationRepo.getCurrentLocation().onSuccess { latLng ->
                                            selectedLocation = latLng
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(latLng, 15f),
                                                durationMs = 500
                                            )
                                        }
                                        isLoadingLocation = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .padding(bottom = 180.dp),
                            containerColor = colorScheme.primaryContainer
                        ) {
                            if (isLoadingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = stringResource(R.string.my_location),
                                    tint = colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Кнопка закрыть
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Color.White
                            )
                        }

                        // Информация о месте и кнопки действий
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    color = colorScheme.surface,
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                )
                                .padding(16.dp)
                        ) {
                            // Адрес
                            Text(
                                text = if (isLoadingAddress) stringResource(R.string.location_determining_address) else address.ifEmpty { stringResource(R.string.location_select_on_map) },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (address.isEmpty() && !isLoadingAddress)
                                    colorScheme.onSurface.copy(alpha = 0.5f)
                                else
                                    colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Координаты
                            selectedLocation?.let { loc ->
                                Text(
                                    text = "${String.format("%.6f", loc.latitude)}, ${String.format("%.6f", loc.longitude)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Кнопки
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Отправить выбранное место
                                Button(
                                    onClick = {
                                        selectedLocation?.let { location ->
                                            onLocationSelected(
                                                LocationData(
                                                    latLng = location,
                                                    address = address.ifEmpty {
                                                        "${location.latitude}, ${location.longitude}"
                                                    }
                                                )
                                            )
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = selectedLocation != null && !isLoadingAddress
                                ) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.send_location))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
