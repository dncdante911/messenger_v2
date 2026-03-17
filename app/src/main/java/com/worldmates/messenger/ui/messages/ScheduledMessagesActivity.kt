package com.worldmates.messenger.ui.messages

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.ui.groups.components.CreateScheduledPostDialog
import com.worldmates.messenger.ui.groups.components.ScheduledPostsScreen
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager
import com.worldmates.messenger.data.model.ScheduledPost

/**
 * ScheduledMessagesActivity — повноекранне управління запланованими повідомленнями.
 *
 * Параметри Intent:
 *   chat_id:   Long   — ідентифікатор чату
 *   chat_type: String — "dm" | "group" | "channel"
 */
class ScheduledMessagesActivity : AppCompatActivity() {

    private lateinit var viewModel: ScheduledMessagesViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val chatId   = intent.getLongExtra("chat_id", 0L)
        val chatType = intent.getStringExtra("chat_type") ?: "dm"

        if (chatId == 0L) {
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[ScheduledMessagesViewModel::class.java]
        viewModel.init(chatId, chatType)

        setContent {
            WorldMatesThemedApp {
                val posts by viewModel.posts.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()

                var showCreateDialog by remember { mutableStateOf(false) }
                var editingPost by remember { mutableStateOf<ScheduledPost?>(null) }

                ScheduledPostsScreen(
                    posts           = posts,
                    isLoading       = isLoading,
                    onBackClick     = { finish() },
                    onCreateClick   = { showCreateDialog = true },
                    onEditClick     = { post ->
                        editingPost     = post
                        showCreateDialog = true
                    },
                    onDeleteClick       = { post -> viewModel.deletePost(post) },
                    onPublishNowClick   = { post -> viewModel.publishNow(post) }
                )

                if (showCreateDialog) {
                    val postToEdit = editingPost
                    CreateScheduledPostDialog(
                        existingPost = postToEdit,
                        onDismiss    = {
                            showCreateDialog = false
                            editingPost      = null
                        },
                        onSave = { text, scheduledTime, mediaUrl, repeatType, isPinned, notifyMembers ->
                            if (postToEdit != null) {
                                viewModel.updatePost(
                                    post          = postToEdit,
                                    text          = text,
                                    scheduledTime = scheduledTime,
                                    repeatType    = repeatType,
                                    isPinned      = isPinned,
                                    notifyMembers = notifyMembers
                                )
                            } else {
                                viewModel.createPost(
                                    text          = text,
                                    scheduledTime = scheduledTime,
                                    mediaUrl      = mediaUrl,
                                    repeatType    = repeatType,
                                    isPinned      = isPinned,
                                    notifyMembers = notifyMembers
                                )
                            }
                            showCreateDialog = false
                            editingPost      = null
                        }
                    )
                }
            }
        }
    }
}
