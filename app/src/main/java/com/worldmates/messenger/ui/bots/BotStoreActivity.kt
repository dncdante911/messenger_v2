package com.worldmates.messenger.ui.bots

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.ui.messages.MessagesActivity
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * BotStoreActivity - Bot Store (Telegram-style)
 *
 * Entry point for users:
 * - Bot catalog (search, categories, popular)
 * - My Bots management
 * - Create new bot
 * - Click bot -> open chat (MessagesActivity with is_bot=true)
 *
 * Access:
 * - From ChatsActivity via SmartToy FAB on Chats tab
 * - From search / menu
 */
class BotStoreActivity : AppCompatActivity() {

    private lateinit var botViewModel: BotViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initialize(this)

        botViewModel = ViewModelProvider(this).get(BotViewModel::class.java)
        botViewModel.loadBotCatalog()

        setContent {
            WorldMatesThemedApp {
                BotStoreScreen()
            }
        }
    }

    @Composable
    private fun BotStoreScreen() {
        var currentScreen by remember { mutableStateOf<BotScreen>(BotScreen.Catalog) }

        // Collect state flows from ViewModel
        val catalogState by botViewModel.catalogState.collectAsState()
        val botDetailState by botViewModel.botDetailState.collectAsState()
        val myBotsState by botViewModel.myBotsState.collectAsState()
        val createBotState by botViewModel.createBotState.collectAsState()

        when (val screen = currentScreen) {
            is BotScreen.Catalog -> {
                BotCatalogScreen(
                    catalogState = catalogState,
                    onSearch = { query -> botViewModel.searchBots(query) },
                    onCategorySelected = { cat -> botViewModel.filterByCategory(cat) },
                    onBotClick = { bot ->
                        botViewModel.loadBotDetails(botId = bot.botId)
                        currentScreen = BotScreen.Detail(bot.botId)
                    },
                    onCreateBotClick = {
                        botViewModel.resetCreateForm()
                        currentScreen = BotScreen.CreateBot
                    },
                    onMyBotsClick = {
                        botViewModel.loadMyBots()
                        currentScreen = BotScreen.MyBots
                    },
                    onBack = { finish() }
                )
            }
            is BotScreen.Detail -> {
                BotDetailScreen(
                    state = botDetailState,
                    onStartChat = { bot ->
                        startActivity(Intent(this@BotStoreActivity, MessagesActivity::class.java).apply {
                            putExtra("recipient_id", bot.botId.hashCode().toLong())
                            putExtra("recipient_name", bot.displayName)
                            putExtra("recipient_avatar", bot.avatar ?: "")
                            putExtra("is_bot", true)
                            putExtra("bot_id", bot.botId)
                            putExtra("bot_username", bot.username)
                        })
                    },
                    onBack = {
                        currentScreen = BotScreen.Catalog
                    }
                )
            }
            is BotScreen.MyBots -> {
                BotManagementScreen(
                    state = myBotsState,
                    onCreateBot = {
                        botViewModel.resetCreateForm()
                        currentScreen = BotScreen.CreateBot
                    },
                    onEditBot = { bot ->
                        botViewModel.loadBotDetails(botId = bot.botId)
                        currentScreen = BotScreen.Detail(bot.botId)
                    },
                    onDeleteBot = { botId -> botViewModel.deleteBot(botId) },
                    onRegenerateToken = { botId -> botViewModel.regenerateToken(botId) },
                    onBack = {
                        currentScreen = BotScreen.Catalog
                    }
                )
            }
            is BotScreen.CreateBot -> {
                CreateBotScreen(
                    state = createBotState,
                    onUpdateForm = { username, displayName, description, about, category, isPublic, canJoinGroups ->
                        botViewModel.updateCreateForm(
                            username = username,
                            displayName = displayName,
                            description = description,
                            about = about,
                            category = category,
                            isPublic = isPublic,
                            canJoinGroups = canJoinGroups
                        )
                    },
                    onCreateBot = { botViewModel.createBot() },
                    onBack = {
                        currentScreen = BotScreen.MyBots
                    }
                )
            }
        }
    }
}

/**
 * Navigation screens inside BotStoreActivity
 */
sealed class BotScreen {
    object Catalog : BotScreen()
    data class Detail(val botId: String) : BotScreen()
    object MyBots : BotScreen()
    object CreateBot : BotScreen()
}
