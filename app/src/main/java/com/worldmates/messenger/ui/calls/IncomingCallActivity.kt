package com.worldmates.messenger.ui.calls

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp

/**
 * 📞 Activity для відображення вхідного дзвінка
 *
 * Показується поверх інших екранів коли приходить вхідний дзвінок
 * Має кнопки Accept та Decline
 * Грає ringtone та вібрує
 */
class IncomingCallActivity : ComponentActivity() {

    private val callsViewModel: CallsViewModel by viewModels()
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "IncomingCallActivity"
        const val EXTRA_FROM_ID = "from_id"
        const val EXTRA_FROM_NAME = "from_name"
        const val EXTRA_FROM_AVATAR = "from_avatar"
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_ROOM_NAME = "room_name"
        const val EXTRA_SDP_OFFER = "sdp_offer"

        /**
         * Створити Intent для запуску IncomingCallActivity
         */
        fun createIntent(
            context: Context,
            fromId: Int,
            fromName: String,
            fromAvatar: String,
            callType: String,
            roomName: String,
            sdpOffer: String?
        ): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FROM_ID, fromId)
                putExtra(EXTRA_FROM_NAME, fromName)
                putExtra(EXTRA_FROM_AVATAR, fromAvatar)
                putExtra(EXTRA_CALL_TYPE, callType)
                putExtra(EXTRA_ROOM_NAME, roomName)
                putExtra(EXTRA_SDP_OFFER, sdpOffer)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Отримати дані про дзвінок з Intent
        val fromId = intent.getIntExtra(EXTRA_FROM_ID, 0)
        val fromName = intent.getStringExtra(EXTRA_FROM_NAME) ?: "Unknown"
        val fromAvatar = intent.getStringExtra(EXTRA_FROM_AVATAR) ?: ""
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "audio"
        val roomName = intent.getStringExtra(EXTRA_ROOM_NAME) ?: ""
        val sdpOffer = intent.getStringExtra(EXTRA_SDP_OFFER)

        Log.d(TAG, "📞 Incoming call from: $fromName (ID: $fromId), type: $callType, room: $roomName")

        // Запустити ringtone та вібрацію
        startRingtoneAndVibration()

        // Показати UI
        setContent {
            WorldMatesThemedApp {
                IncomingCallScreen(
                    fromName = fromName,
                    fromAvatar = fromAvatar,
                    callType = callType,
                    onAccept = {
                        stopRingtoneAndVibration()
                        acceptCall(fromId, fromName, fromAvatar, callType, roomName, sdpOffer)
                    },
                    onDecline = {
                        stopRingtoneAndVibration()
                        declineCall(roomName)
                    }
                )
            }
        }
    }

    /**
     * 🔔 Запустити ringtone та вібрацію
     */
    private fun startRingtoneAndVibration() {
        try {
            // Ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.play()
            Log.d(TAG, "🔔 Ringtone started")

            // Vibration
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(
                    longArrayOf(0, 1000, 1000),  // Пауза, вібрація, пауза
                    0  // Повторювати з індексу 0
                )
                vibrator?.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0)
            }
            Log.d(TAG, "📳 Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ringtone/vibration", e)
        }
    }

    /**
     * 🔕 Зупинити ringtone та вібрацію
     */
    private fun stopRingtoneAndVibration() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
            Log.d(TAG, "🔕 Ringtone and vibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone/vibration", e)
        }
    }

    /**
     * ✅ Прийняти дзвінок
     *
     * ВАЖЛИВО: НЕ викликаємо acceptCall() тут!
     * CallsActivity сама ініціалізує WebRTC і відправить call:accept
     * Це вирішує проблему з різними ViewModel instances
     */
    private fun acceptCall(
        fromId: Int,
        fromName: String,
        fromAvatar: String,
        callType: String,
        roomName: String,
        sdpOffer: String?
    ) {
        Log.d(TAG, "✅ Call accepted, starting CallsActivity...")

        // ✅ ВИПРАВЛЕНО: НЕ викликаємо acceptCall() тут!
        // CallsActivity має свій ViewModel і сама виконає:
        // 1. Отримання ICE серверів
        // 2. Створення PeerConnection
        // 3. Встановлення remote SDP (offer)
        // 4. Створення local media stream
        // 5. Створення answer
        // 6. Відправка call:accept на сервер

        // Запустити CallsActivity для активного дзвінка
        val intent = Intent(this, CallsActivity::class.java).apply {
            putExtra("is_incoming", true)
            putExtra("from_id", fromId)
            putExtra("from_name", fromName)
            putExtra("from_avatar", fromAvatar)
            putExtra("call_type", callType)
            putExtra("room_name", roomName)
            putExtra("sdp_offer", sdpOffer)
        }
        startActivity(intent)
        finish()
    }

    /**
     * ❌ Відхилити дзвінок
     */
    private fun declineCall(roomName: String) {
        Log.d(TAG, "❌ Call declined")

        // Відправити call:reject через CallsViewModel
        callsViewModel.rejectCall(roomName)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
    }
}

/**
 * 🎨 UI екрану вхідного дзвінка
 */
@Composable
fun IncomingCallScreen(
    fromName: String,
    fromAvatar: String,
    callType: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Аватар
            if (fromAvatar.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(fromAvatar),
                    contentDescription = "Caller avatar",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder якщо немає аватара
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fromName.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Ім'я
            Text(
                text = fromName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Тип дзвінка
            Text(
                text = if (callType == "video") "📹 Video call" else "📞 Audio call",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Кнопки
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                // Відхилити
                FloatingActionButton(
                    onClick = onDecline,
                    containerColor = Color.Red,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Прийняти
                FloatingActionButton(
                    onClick = onAccept,
                    containerColor = Color.Green,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Accept",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
