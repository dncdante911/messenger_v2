package com.worldmates.messenger.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.network.FileManager
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.VoicePlayer
import com.worldmates.messenger.services.MessageNotificationService
import com.worldmates.messenger.utils.VoiceRecorder
import com.worldmates.messenger.utils.LanguageManager
import com.worldmates.messenger.R

/**
 * ✅ НОВИЙ MessagesActivity - wrapper для MessagesScreen з Phase 2
 *
 * Цей файл створено для сумісності з навігацією з інших активностей
 * (ChatsActivity, GroupsActivity, MyFirebaseMessagingService).
 *
 * Використовує новий MessagesScreen з усіма функціями Phase 2:
 * - Emoji Picker, Sticker Picker
 * - Реакції емоджі
 * - Свайп для відповіді
 * - Галочки прочитання
 * - Анімації
 */
class MessagesActivity : AppCompatActivity() {

    private lateinit var viewModel: MessagesViewModel
    private lateinit var fileManager: FileManager
    private lateinit var voiceRecorder: VoiceRecorder
    private lateinit var voicePlayer: VoicePlayer

    private var recipientId: Long = 0
    private var groupId: Long = 0
    private var channelId: Long = 0
    private var topicId: Long = 0 // Subgroup/Topic ID for topic-based chats
    private var topicName: String = "" // Topic name to show in header
    private var recipientName: String = ""
    private var recipientAvatar: String = ""
    private var isGroup: Boolean = false
    private var isChannel: Boolean = false
    private var isBusinessChat: Boolean = false
    private var isBotChat: Boolean = false
    private var botDescription: String? = null

    // Permission request launcher
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.mic_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.mic_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val videoPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        if (cameraGranted) {
            if (!micGranted) {
                Toast.makeText(
                    this,
                    getString(R.string.camera_no_mic_warning),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                this,
                getString(R.string.camera_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Дозволяємо Compose керувати window insets (клавіатура, навігація)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Отримуємо параметри з Intent
        recipientId = intent.getLongExtra("recipient_id", 0)
        groupId = intent.getLongExtra("group_id", 0)
        channelId = intent.getLongExtra("channel_id", 0)
        topicId = intent.getLongExtra("topic_id", 0)
        topicName = intent.getStringExtra("topic_name") ?: ""
        recipientName = intent.getStringExtra("recipient_name") ?: "Unknown"
        recipientAvatar = intent.getStringExtra("recipient_avatar") ?: ""
        isGroup = intent.getBooleanExtra("is_group", false)
        isChannel = intent.getBooleanExtra("is_channel", false)
        isBusinessChat = intent.getBooleanExtra("is_business_chat", false)
        isBotChat = intent.getBooleanExtra("is_bot", false)
        botDescription = intent.getStringExtra("bot_description")

        // Ініціалізуємо утиліти
        fileManager = FileManager(this)
        voiceRecorder = VoiceRecorder(this)
        voicePlayer = VoicePlayer(this)

        viewModel = ViewModelProvider(this).get(MessagesViewModel::class.java)

        // Завантажуємо повідомлення
        if (isGroup) {
            viewModel.initializeGroup(groupId, topicId)
        } else {
            viewModel.initialize(recipientId, isBusinessChat)
        }

        // Ініціалізуємо ThemeManager
        ThemeManager.initialize(this)

        setContent {
            WorldMatesThemedApp {
                MessagesScreenWrapper()
            }
        }
    }

    @Composable
    private fun MessagesScreenWrapper() {
        // Використовуємо новий MessagesScreen з Phase 2 функціями
        // Launchers тепер створюються всередині MessagesScreen
        MessagesScreen(
            viewModel = viewModel,
            fileManager = fileManager,
            voiceRecorder = voiceRecorder,
            voicePlayer = voicePlayer,
            recipientName = recipientName,
            recipientAvatar = recipientAvatar,
            isGroup = isGroup,
            isBusinessChat = isBusinessChat,
            isBotChat = isBotChat,
            botDescription = botDescription,
            onBackPressed = { finish() },
            onRequestAudioPermission = { requestAudioPermission() },
            onRequestVideoPermissions = { requestVideoPermissions() }
        )
    }

    /**
     * Перевіряє та запитує дозвіл на запис аудіо
     * @return true якщо дозвіл вже є, false якщо треба запитати
     */
    private fun requestAudioPermission(): Boolean {
        return when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Дозвіл вже є
                true
            }
            else -> {
                // Запитуємо дозвіл
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                false
            }
        }
    }

    /**
     * Перевіряє та запитує дозволи для відеоповідомлень.
     * Для старту потрібна камера; мікрофон — опційно (для запису звуку).
     */
    private fun requestVideoPermissions(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val micGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (cameraGranted) {
            if (!micGranted) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            return true
        }

        videoPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
        return false
    }

    /**
     * Повідомляємо сервіс сповіщень, який чат відкрито,
     * щоб не показувати дублюючі нотифікації.
     * Також одразу скасовуємо вже показане сповіщення для цього чату —
     * setAutoCancel(true) прибирає його лише при тапі по ньому,
     * але не коли чат відкрито інакше (з ChatsActivity, пуш-сервісу тощо).
     */
    override fun onResume() {
        super.onResume()
        val notifId: Int = when {
            isChannel -> {
                MessageNotificationService.activeChannelId = channelId
                (channelId + 200_000L).toInt()
            }
            isGroup -> {
                MessageNotificationService.activeGroupId = groupId
                (groupId + 100_000L).toInt()
            }
            else -> {
                MessageNotificationService.activeRecipientId = recipientId
                recipientId.toInt()
            }
        }
        NotificationManagerCompat.from(this).cancel(notifId)
    }

    /**
     * Якщо активність вже запущена (FLAG_ACTIVITY_CLEAR_TOP) і прийшов
     * новий інтент (тап на інше сповіщення) — оновлюємо змінні та
     * скасовуємо відповідне сповіщення.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-read parameters from the new intent so onResume cancels the right notif
        recipientId = intent.getLongExtra("recipient_id", recipientId)
        groupId     = intent.getLongExtra("group_id", groupId)
        channelId   = intent.getLongExtra("channel_id", channelId)
        isGroup     = intent.getBooleanExtra("is_group", isGroup)
        isChannel   = intent.getBooleanExtra("is_channel", isChannel)
        // onResume() is called automatically after onNewIntent — it will cancel the notif
    }

    /**
     * Скидаємо фільтр, щоб сповіщення знову показувались коли
     * користувач іде з чату.
     */
    override fun onPause() {
        super.onPause()
        MessageNotificationService.activeRecipientId = 0
        MessageNotificationService.activeGroupId     = 0
        MessageNotificationService.activeChannelId   = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        voicePlayer.release()
        fileManager.clearOldCacheFiles()
    }
}
