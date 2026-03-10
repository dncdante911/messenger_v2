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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * 📞 Activity для входящего группового звонка.
 * Показывается поверх других экранов с фиолетовым дизайном
 * для визуального отличия от 1-на-1 звонков.
 */
class IncomingGroupCallActivity : ComponentActivity() {

    private val callsViewModel: CallsViewModel by viewModels()
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "IncomingGroupCallActivity"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupId = intent.getIntExtra("group_id", 0)
        val groupName = intent.getStringExtra("group_name") ?: getString(R.string.group_default_name)
        val initiatedBy = intent.getIntExtra("initiated_by", 0)
        val initiatorName = intent.getStringExtra("initiator_name") ?: "Unknown"
        val initiatorAvatar = intent.getStringExtra("initiator_avatar") ?: ""
        val callType = intent.getStringExtra("call_type") ?: "audio"
        val roomName = intent.getStringExtra("room_name") ?: ""
        val maxParticipants = intent.getIntExtra("max_participants", 5)
        val isPremiumCall = intent.getBooleanExtra("is_premium_call", false)

        Log.d(TAG, "📞 Incoming group call from $initiatorName for group $groupName (room=$roomName)")

        if (roomName.isEmpty() || groupId == 0) {
            Log.e(TAG, "Invalid group call data, closing")
            finish()
            return
        }

        startRingtoneAndVibration()

        setContent {
            WorldMatesThemedApp {
                IncomingGroupCallScreen(
                    groupName = groupName,
                    initiatorName = initiatorName,
                    initiatorAvatar = initiatorAvatar,
                    callType = callType,
                    maxParticipants = maxParticipants,
                    isPremiumCall = isPremiumCall,
                    onAccept = {
                        stopRingtoneAndVibration()
                        acceptGroupCall(
                            groupId = groupId,
                            groupName = groupName,
                            initiatedBy = initiatedBy,
                            initiatorName = initiatorName,
                            initiatorAvatar = initiatorAvatar,
                            callType = callType,
                            roomName = roomName,
                            maxParticipants = maxParticipants,
                            isPremiumCall = isPremiumCall
                        )
                    },
                    onDecline = {
                        stopRingtoneAndVibration()
                        declineGroupCall()
                    }
                )
            }
        }
    }

    private fun startRingtoneAndVibration() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.play()

            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0)
                )
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

    private fun acceptGroupCall(
        groupId: Int,
        groupName: String,
        initiatedBy: Int,
        initiatorName: String,
        initiatorAvatar: String,
        callType: String,
        roomName: String,
        maxParticipants: Int,
        isPremiumCall: Boolean
    ) {
        Log.d(TAG, "✅ Group call accepted, launching CallsActivity with group mode")

        val intent = Intent(this, CallsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("isGroup", true)
            putExtra("groupId", groupId.toLong())
            putExtra("recipientName", groupName)
            putExtra("callType", callType)
            // Pass room_name so CallsActivity knows we're joining an existing call
            putExtra("group_room_name", roomName)
            putExtra("group_initiated_by", initiatedBy)
            putExtra("group_initiator_name", initiatorName)
            putExtra("group_max_participants", maxParticipants)
            putExtra("is_incoming_group", true)
        }
        startActivity(intent)
        finish()
    }

    private fun declineGroupCall() {
        Log.d(TAG, "❌ Group call declined")
        // No need to send a reject — just don't join
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
    }
}

@Composable
fun IncomingGroupCallScreen(
    groupName: String,
    initiatorName: String,
    initiatorAvatar: String,
    callType: String,
    maxParticipants: Int,
    isPremiumCall: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D0D2B), Color(0xFF1A0A3D), Color(0xFF0D1F3D))
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {

            // Иконка группы
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7C4DFF).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                if (initiatorAvatar.isNotEmpty()) {
                    AsyncImage(
                        model = initiatorAvatar,
                        contentDescription = initiatorName,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            // Бейджи
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.group_call_badge),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF7C4DFF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                if (isPremiumCall) {
                    Text(
                        text = stringResource(R.string.group_call_pro_up_to, maxParticipants),
                        color = Color(0xFFFFD700),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFFFD700).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.group_call_up_to_participants, maxParticipants),
                        color = Color(0xFF9E9EC0),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Название группы
            Text(
                text = groupName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Кто звонит
            Text(
                text = stringResource(R.string.group_call_invites_you, initiatorName),
                fontSize = 15.sp,
                color = Color(0xFF9E9EC0)
            )

            // Тип звонка
            Text(
                text = if (callType == "video") stringResource(R.string.group_call_video)
                       else stringResource(R.string.group_call_audio),
                fontSize = 14.sp,
                color = Color(0xFF7C4DFF)
            )

            Spacer(Modifier.height(32.dp))

            // Кнопки
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Отклонить
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = Color(0xFFFF4444),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = stringResource(R.string.group_call_decline),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.group_call_decline), color = Color(0xFFFF4444), fontSize = 13.sp)
                }

                // Прийняти
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onAccept,
                        containerColor = Color(0xFF4CAF50),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = stringResource(R.string.group_call_accept),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.group_call_accept), color = Color(0xFF4CAF50), fontSize = 13.sp)
                }
            }
        }
    }
}
