package com.worldmates.messenger.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.network.FileManager
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.VoicePlayer
import com.worldmates.messenger.utils.VoiceRecorder

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
    private var topicId: Long = 0 // Subgroup/Topic ID for topic-based chats
    private var topicName: String = "" // Topic name to show in header
    private var recipientName: String = ""
    private var recipientAvatar: String = ""
    private var isGroup: Boolean = false

    // Permission request launcher
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "✅ Дозвіл на мікрофон надано", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "⚠️ Для голосових повідомлень потрібен дозвіл на мікрофон",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Дозволяємо Compose керувати window insets (клавіатура, навігація)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Отримуємо параметри з Intent
        recipientId = intent.getLongExtra("recipient_id", 0)
        groupId = intent.getLongExtra("group_id", 0)
        topicId = intent.getLongExtra("topic_id", 0)
        topicName = intent.getStringExtra("topic_name") ?: ""
        recipientName = intent.getStringExtra("recipient_name") ?: "Unknown"
        recipientAvatar = intent.getStringExtra("recipient_avatar") ?: ""
        isGroup = intent.getBooleanExtra("is_group", false)

        // Ініціалізуємо утиліти
        fileManager = FileManager(this)
        voiceRecorder = VoiceRecorder(this)
        voicePlayer = VoicePlayer(this)

        viewModel = ViewModelProvider(this).get(MessagesViewModel::class.java)

        // Завантажуємо повідомлення
        if (isGroup) {
            viewModel.initializeGroup(groupId, topicId)
        } else {
            viewModel.initialize(recipientId)
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
            onBackPressed = { finish() },
            onRequestAudioPermission = { requestAudioPermission() }
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

    override fun onDestroy() {
        super.onDestroy()
        voicePlayer.release()
        fileManager.clearOldCacheFiles()
    }
}
