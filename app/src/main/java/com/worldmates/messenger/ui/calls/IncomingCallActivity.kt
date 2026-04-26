package com.worldmates.messenger.ui.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.services.MessageNotificationService
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * Full-screen incoming call activity.
 *
 * Design: Telegram-style — dark gradient background, large avatar with
 * animated pulse rings, caller name + call type, accept/decline FABs.
 *
 * Bug fixes applied:
 * 1. Cancels call notification on accept/decline so the ongoing notification
 *    doesn't linger in the shade and re-launch this activity.
 * 2. Listens for ACTION_CALL_ACCEPTED_ELSEWHERE broadcast (sent by the service
 *    when call:accepted_elsewhere arrives) and finishes immediately — prevents
 *    a stale call screen after accepting on another device/socket.
 */
class IncomingCallActivity : ComponentActivity() {

    private val callsViewModel: CallsViewModel by viewModels()
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "IncomingCallActivity"
        const val EXTRA_FROM_ID     = "from_id"
        const val EXTRA_FROM_NAME   = "from_name"
        const val EXTRA_FROM_AVATAR = "from_avatar"
        const val EXTRA_CALL_TYPE   = "call_type"
        const val EXTRA_ROOM_NAME   = "room_name"
        const val EXTRA_SDP_OFFER   = "sdp_offer"

        fun createIntent(
            context:    Context,
            fromId:     Int,
            fromName:   String,
            fromAvatar: String,
            callType:   String,
            roomName:   String,
            sdpOffer:   String?
        ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_ID,     fromId)
            putExtra(EXTRA_FROM_NAME,   fromName)
            putExtra(EXTRA_FROM_AVATAR, fromAvatar)
            putExtra(EXTRA_CALL_TYPE,   callType)
            putExtra(EXTRA_ROOM_NAME,   roomName)
            putExtra(EXTRA_SDP_OFFER,   sdpOffer)
        }
    }

    // Dismiss this activity when the call was accepted on another socket/device
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "📞 call:accepted_elsewhere — finishing IncomingCallActivity")
            stopRingtoneAndVibration()
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fromId     = intent.getIntExtra(EXTRA_FROM_ID, 0)
        val fromName   = intent.getStringExtra(EXTRA_FROM_NAME)   ?: "Unknown"
        val fromAvatar = intent.getStringExtra(EXTRA_FROM_AVATAR) ?: ""
        val callType   = intent.getStringExtra(EXTRA_CALL_TYPE)   ?: "audio"
        val roomName   = intent.getStringExtra(EXTRA_ROOM_NAME)   ?: ""
        val sdpOffer   = intent.getStringExtra(EXTRA_SDP_OFFER)

        Log.d(TAG, "📞 Incoming call from: $fromName ($fromId) type=$callType room=$roomName")

        // Observe callDismissed from ViewModel (call:accepted_elsewhere via ViewModel socket)
        callsViewModel.callDismissed.observe(this) { dismissedRoom ->
            if (dismissedRoom == roomName) {
                Log.d(TAG, "📞 ViewModel signalled dismiss for room $roomName")
                stopRingtoneAndVibration()
                finish()
            }
        }

        startRingtoneAndVibration()

        setContent {
            WorldMatesThemedApp {
                IncomingCallScreen(
                    fromName   = fromName,
                    fromAvatar = fromAvatar,
                    callType   = callType,
                    onAccept   = {
                        stopRingtoneAndVibration()
                        MessageNotificationService.dismissCallNotifications(this)
                        acceptCall(fromId, fromName, fromAvatar, callType, roomName, sdpOffer)
                    },
                    onDecline  = {
                        stopRingtoneAndVibration()
                        MessageNotificationService.dismissCallNotifications(this)
                        declineCall(roomName)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MessageNotificationService.ACTION_CALL_ACCEPTED_ELSEWHERE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(dismissReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(dismissReceiver) } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop: same call relaunched — nothing to do, already showing
        Log.d(TAG, "onNewIntent — already showing, ignoring")
    }

    private fun startRingtoneAndVibration() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()

            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ringtone/vibration", e)
        }
    }

    private fun stopRingtoneAndVibration() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone/vibration", e)
        }
    }

    private fun acceptCall(
        fromId:     Int,
        fromName:   String,
        fromAvatar: String,
        callType:   String,
        roomName:   String,
        sdpOffer:   String?
    ) {
        Log.d(TAG, "✅ Call accepted, starting CallsActivity")
        startActivity(Intent(this, CallsActivity::class.java).apply {
            putExtra("is_incoming",  true)
            putExtra("from_id",      fromId)
            putExtra("from_name",    fromName)
            putExtra("from_avatar",  fromAvatar)
            putExtra("call_type",    callType)
            putExtra("room_name",    roomName)
            putExtra("sdp_offer",    sdpOffer)
        })
        finish()
    }

    private fun declineCall(roomName: String) {
        Log.d(TAG, "❌ Call declined")
        callsViewModel.rejectCall(roomName)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
    }
}

// ── UI ────────────────────────────────────────────────────────────────────────

@Composable
fun IncomingCallScreen(
    fromName:   String,
    fromAvatar: String,
    callType:   String,
    onAccept:   () -> Unit,
    onDecline:  () -> Unit
) {
    // Pulse animation for the avatar ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = EaseOut), RepeatMode.Restart
        ), label = "pulse1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            tween(1200, delayMillis = 400, easing = EaseOut), RepeatMode.Restart
        ), label = "pulse2"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseOut), RepeatMode.Restart),
        label = "alpha1"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.22f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(1200, delayMillis = 400, easing = EaseOut), RepeatMode.Restart
        ), label = "alpha2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
                )
            )
    ) {
        // Blurred background avatar (full width, low opacity)
        if (fromAvatar.isNotEmpty()) {
            AsyncImage(
                model             = fromAvatar,
                contentDescription = null,
                modifier          = Modifier.fillMaxSize(),
                contentScale      = ContentScale.Crop,
                alpha             = 0.12f
            )
        }

        Column(
            modifier              = Modifier.fillMaxSize(),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.SpaceBetween
        ) {
            // ── Top section: caller info ──────────────────────────────────────
            Column(
                modifier            = Modifier.padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Call type label
                Text(
                    text      = if (callType == "video") "Incoming video call" else "Incoming call",
                    fontSize  = 15.sp,
                    color     = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )

                // Avatar with pulse rings
                Box(contentAlignment = Alignment.Center) {
                    // Outer pulse ring
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .scale(pulseScale2)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = pulseAlpha2))
                    )
                    // Inner pulse ring
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .scale(pulseScale1)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = pulseAlpha1))
                    )
                    // Avatar
                    if (fromAvatar.isNotEmpty()) {
                        AsyncImage(
                            model              = fromAvatar,
                            contentDescription = "Caller avatar",
                            modifier           = Modifier
                                .size(160.dp)
                                .clip(CircleShape),
                            contentScale       = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A5298)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = fromName.firstOrNull()?.uppercase() ?: "?",
                                fontSize   = 64.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White
                            )
                        }
                    }
                }

                // Caller name
                Text(
                    text       = fromName,
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }

            // ── Bottom section: action buttons ────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 72.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick          = onDecline,
                        containerColor   = Color(0xFFE53935),
                        modifier         = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint               = Color.White,
                            modifier           = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Decline", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick          = onAccept,
                        containerColor   = Color(0xFF43A047),
                        modifier         = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Call,
                            contentDescription = "Accept",
                            tint               = Color.White,
                            modifier           = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Accept", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }
    }
}
