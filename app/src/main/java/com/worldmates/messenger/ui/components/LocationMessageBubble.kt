package com.worldmates.messenger.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.worldmates.messenger.R

/**
 * Bubble for received/sent location messages (type = "map").
 * Shows a non-interactive Google Map thumbnail with a marker,
 * the address below, and opens Google Maps on tap.
 */
@Composable
fun LocationMessageBubble(
    lat: Double,
    lng: Double,
    address: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val position = remember(lat, lng) { LatLng(lat, lng) }
    val cameraState = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(position, 14f)
    }

    Card(
        modifier = modifier
            .width(240.dp)
            .clickable {
                // Open Google Maps (or any maps app) at the coordinates
                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    // Fallback: open in browser
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
                    )
                }
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // ── Map thumbnail ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled   = false,
                        zoomGesturesEnabled   = false,
                        scrollGesturesEnabled = false,
                        tiltGesturesEnabled   = false,
                        rotationGesturesEnabled = false,
                        compassEnabled        = false,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled     = false,
                    ),
                    properties = MapProperties(isMyLocationEnabled = false),
                ) {
                    Marker(
                        state = MarkerState(position = position),
                        title = address ?: "$lat, $lng",
                    )
                }

                // Tap overlay hint
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.location_open_maps),
                        color = Color.White,
                        fontSize = 10.sp,
                    )
                }
            }

            // ── Address / coordinates row ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = address?.takeIf { it.isNotBlank() }
                        ?: "%.5f, %.5f".format(lat, lng),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
