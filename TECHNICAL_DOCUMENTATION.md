# WorldMates Messenger v2.0 - Полная Техническая Документация

**Версия документации:** 1.0  
**Последнее обновление:** 2026-04-04  
**Целевая аудитория:** Android разработчики, Backend разработчики

---

## 📑 ОГЛАВЛЕНИЕ

1. [Структура проекта](#структура-проекта)
2. [Ключевые компоненты](#ключевые-компоненты)
3. [Архитектура](#архитектура)
4. [ViewModels & State Management](#viewmodels--state-management)
5. [Network Layer](#network-layer)
6. [Database Layer](#database-layer)
7. [UI Components](#ui-components)
8. [Services](#services)
9. [Security](#security)
10. [Как расширять функциональность](#как-расширять-функциональность)

---

## СТРУКТУРА ПРОЕКТА

### Gradle Modules
```
messenger_v2/
├── app/                          # Main Android app module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/            # Kotlin source code
│   │   │   ├── res/             # Resources (layouts, strings, etc)
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                # Unit tests
│   │   └── androidTest/         # Instrumented tests
│   └── build.gradle             # App-level build config
├── build.gradle                 # Project-level build config
├── settings.gradle              # Gradle settings
└── ...other files
```

### Directory Structure (Source Code)

```
app/src/main/java/com/worldmates/messenger/
│
├── MainActivity.kt                           # Entry point
│
├── ui/                                      # UI Layer (Compose)
│   ├── chats/
│   │   ├── ChatsActivity.kt               # Main chats activity
│   │   ├── ChatsScreenModern.kt           # Chats list screen (Compose)
│   │   ├── ChatsViewModel.kt              # ViewModel для чатов
│   │   ├── ModernChatsUI.kt               # Reusable UI components
│   │   ├── BottomNavBar.kt                # Navigation bar
│   │   └── ...other files
│   │
│   ├── messages/
│   │   ├── MessagesScreen.kt              # Message list screen
│   │   ├── MessagesViewModel.kt           # Message logic
│   │   ├── MessageBubbles.kt              # Message UI components
│   │   └── ...other files
│   │
│   ├── groups/
│   │   ├── GroupsViewModel.kt             # Group management logic
│   │   ├── GroupsScreen.kt                # Groups list
│   │   ├── GroupDetailsScreen.kt          # Group info screen
│   │   └── ...other files
│   │
│   ├── channels/
│   │   ├── ChannelsViewModel.kt           # Channel logic
│   │   ├── ChannelsScreen.kt              # Channel list
│   │   ├── ChannelDetailsScreen.kt        # Channel info & posts
│   │   └── ...other files
│   │
│   ├── profile/
│   │   ├── UserProfileScreen.kt           # User profile view
│   │   ├── UserProfileViewModel.kt        # Profile logic
│   │   ├── EditProfileScreen.kt           # Edit profile
│   │   └── ...other files
│   │
│   ├── calls/
│   │   ├── CallsScreen.kt                 # Call interface
│   │   ├── CallsViewModel.kt              # Call logic
│   │   └── ...other files
│   │
│   ├── settings/
│   │   ├── SettingsScreen.kt              # Settings UI
│   │   ├── SettingsViewModel.kt           # Settings logic
│   │   ├── security/                      # Security settings
│   │   └── ...other files
│   │
│   ├── stories/
│   │   ├── StoryViewModel.kt              # Story logic
│   │   ├── StoriesScreen.kt               # Stories display
│   │   └── ...other files
│   │
│   ├── login/
│   │   ├── LoginScreen.kt                 # Login UI
│   │   ├── LoginViewModel.kt              # Login logic
│   │   └── ...other files
│   │
│   ├── components/                         # Reusable UI components
│   │   ├── media/
│   │   │   ├── ImageMessageComponent.kt
│   │   │   ├── VideoMessageComponent.kt
│   │   │   └── ...other files
│   │   ├── formatting/
│   │   │   ├── FormattingToolbar.kt       # Text formatting toolbar
│   │   │   └── ...other files
│   │   ├── EmojiPicker.kt
│   │   ├── LocationPicker.kt
│   │   └── ...other files
│   │
│   └── ...other feature folders
│
├── data/                                   # Data Layer
│   ├── model/                             # Data models
│   │   ├── Message.kt
│   │   ├── Chat.kt
│   │   ├── User.kt
│   │   ├── Group.kt
│   │   ├── Channel.kt
│   │   ├── Story.kt
│   │   ├── CallHistory.kt
│   │   └── ...other models
│   │
│   ├── local/                             # Local database (Room)
│   │   ├── AppDatabase.kt                 # Room database config
│   │   ├── dao/
│   │   │   ├── MessageDao.kt              # Database queries
│   │   │   ├── ChatDao.kt
│   │   │   ├── UserDao.kt
│   │   │   └── ...other DAOs
│   │   └── entity/
│   │       ├── MessageEntity.kt
│   │       ├── ChatEntity.kt
│   │       └── ...other entities
│   │
│   ├── repository/                        # Repository pattern
│   │   ├── MessageRepository.kt           # Abstract data access
│   │   ├── ChatRepository.kt
│   │   ├── UserRepository.kt
│   │   └── ...other repositories
│   │
│   └── backup/
│       ├── CloudBackupManager.kt
│       └── ...other files
│
├── network/                                # Network Layer
│   ├── NodeApi.kt                         # Main REST API service (61KB)
│   ├── NodeGroupApi.kt                    # Group API endpoints
│   ├── NodeChannelApi.kt                  # Channel API endpoints
│   ├── NodeProfileApi.kt                  # Profile API endpoints
│   ├── NodeBusinessApi.kt                 # Business API endpoints
│   ├── NodeStoriesApi.kt                  # Stories API endpoints
│   │
│   ├── SocketManager.kt                   # WebSocket management (58KB)
│   ├── WebRTCManager.kt                   # Video/audio calls (57KB)
│   ├── GroupWebRTCManager.kt              # Group calls (28KB)
│   ├── LivestreamWebRTCManager.kt         # Livestream (20KB)
│   │
│   ├── MediaUploader.kt                   # File upload management
│   ├── MediaLoadingManager.kt             # Media download & cache
│   ├── FileManager.kt                     # File operations
│   │
│   ├── NodeRetrofitClient.kt              # Retrofit HTTP client config
│   ├── TokenRefreshInterceptor.kt         # JWT token refresh
│   ├── NetworkInterceptor.kt              # Request/response logging
│   ├── NetworkQualityMonitor.kt           # Connection quality
│   │
│   ├── BotRepository.kt                   # Bot API
│   ├── StrapiClient.kt                    # CMS client
│   └── ...other files
│
├── services/                               # Background Services
│   ├── MessageNotificationService.kt      # Notification handling (36KB)
│   ├── MusicPlaybackService.kt            # Music player service
│   ├── WMFirebaseMessagingService.kt      # FCM message handling
│   ├── MessageNotificationService.kt      # Push notifications
│   ├── NotificationKeepAliveManager.kt    # Keep connection alive
│   └── ...other services
│
├── utils/                                  # Utility Classes
│   ├── LanguageManager.kt                 # Language/localization
│   ├── EncryptedMediaHandler.kt           # AES-256-GCM encryption
│   ├── DateTimeUtils.kt
│   ├── FileUtils.kt
│   ├── ValidationUtils.kt
│   └── ...other utilities
│
└── res/                                    # Resources
    ├── values/
    │   ├── strings.xml                    # English/Ukrainian strings
    │   ├── colors.xml
    │   ├── dimens.xml
    │   └── ...other resources
    ├── values-ru/
    │   ├── strings.xml                    # Russian translations
    │   └── ...other resources
    ├── drawable/
    ├── layout/
    └── ...other resource folders
```

---

## КЛЮЧЕВЫЕ КОМПОНЕНТЫ

### 1. MESSAGES & CHATS

#### Основной файл: `data/model/Message.kt`

```kotlin
data class Message(
    val id: String,                    // Unique message ID
    val chatId: String,                // Chat ID
    val senderId: String,              // Sender user ID
    val senderName: String,            // Sender display name
    val senderAvatar: String?,         // Sender avatar URL
    val content: String,               // Message text
    val timestamp: Long,               // Unix timestamp
    val isEdited: Boolean,             // Was message edited
    val editedAt: Long?,               // When it was edited
    val isDeleted: Boolean,            // Was message deleted
    val mediaUrls: List<String>,       // Attached media URLs
    val mediaTypes: List<String>,      // Media types (image, video, audio, file)
    val replyToId: String?,            // ID of message being replied to
    val reactions: Map<String, List<String>>, // Map of emoji to user IDs
    val status: MessageStatus,         // SENDING, SENT, DELIVERED, READ
    val readBy: List<String>,          // User IDs who read this message
    val readAt: Long?,                 // When message was read
    val formattingRanges: List<FormattingRange>? // Bold, italic, code, links
)
```

#### ViewModel: `ui/messages/MessagesViewModel.kt`

```kotlin
class MessagesViewModel : ViewModel() {
    // Message list state
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()
    
    // Typing indicator
    private val _isUserTyping = MutableStateFlow(false)
    val isUserTyping = _isUserTyping.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    // Functions
    fun sendMessage(content: String, mediaUrls: List<String> = emptyList())
    fun editMessage(messageId: String, newContent: String)
    fun deleteMessage(messageId: String)
    fun addReaction(messageId: String, emoji: String)
    fun loadMoreMessages() // Pagination
    fun markAsRead(messageId: String)
    fun notifyTyping(isTyping: Boolean)
}
```

#### Network: `network/NodeApi.kt`

```kotlin
interface NodeApi {
    // Отправить сообщение
    @POST("/chats/{chatId}/messages")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Body request: SendMessageRequest
    ): ApiResponse<Message>
    
    // Получить сообщения
    @GET("/chats/{chatId}/messages")
    suspend fun getMessages(
        @Path("chatId") chatId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): ApiResponse<List<Message>>
    
    // Отредактировать сообщение
    @PUT("/chats/{chatId}/messages/{messageId}")
    suspend fun editMessage(
        @Path("chatId") chatId: String,
        @Path("messageId") messageId: String,
        @Body request: EditMessageRequest
    ): ApiResponse<Message>
    
    // Удалить сообщение
    @DELETE("/chats/{chatId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("chatId") chatId: String,
        @Path("messageId") messageId: String
    ): ApiResponse<Unit>
    
    // Добавить реакцию
    @POST("/chats/{chatId}/messages/{messageId}/reactions")
    suspend fun addReaction(
        @Path("chatId") chatId: String,
        @Path("messageId") messageId: String,
        @Body request: AddReactionRequest
    ): ApiResponse<Message>
}
```

#### WebSocket Events: `network/SocketManager.kt`

```kotlin
class SocketManager : SocketIOClient {
    // На сервер
    fun sendMessage(message: Message) {
        socket.emit("message:send", message.toJson())
    }
    
    // От сервера
    fun onMessageReceived(listener: (Message) -> Unit) {
        socket.on("message:new") { args ->
            val message = parseJson<Message>(args[0])
            listener(message)
        }
    }
    
    fun onTypingStart(listener: (userId: String) -> Unit) {
        socket.on("typing:start") { args ->
            val userId = args[0] as String
            listener(userId)
        }
    }
    
    fun onMessageEdited(listener: (Message) -> Unit) {
        socket.on("message:edited") { args ->
            val message = parseJson<Message>(args[0])
            listener(message)
        }
    }
    
    fun onMessageDeleted(listener: (String) -> Unit) {
        socket.on("message:deleted") { args ->
            val messageId = args[0] as String
            listener(messageId)
        }
    }
}
```

### 2. GROUPS

#### Модель: `data/model/Group.kt`

```kotlin
data class Group(
    val id: String,
    val name: String,
    val description: String?,
    val avatar: String?,
    val createdBy: String,
    val createdAt: Long,
    val members: List<GroupMember>,
    val isPrivate: Boolean,
    val settings: GroupSettings
)

data class GroupMember(
    val userId: String,
    val username: String,
    val role: GroupRole,  // OWNER, ADMIN, MODERATOR, MEMBER
    val joinedAt: Long
)

data class GroupSettings(
    val allowMessagesFromMembersOnly: Boolean,
    val allowMediaSharing: Boolean,
    val autoDeleteMessages: Int?, // milliseconds
    val requireApprovalForMembers: Boolean
)
```

#### API: `network/NodeGroupApi.kt`

```kotlin
interface NodeGroupApi {
    @POST("/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): ApiResponse<Group>
    
    @GET("/groups/{groupId}")
    suspend fun getGroupDetails(@Path("groupId") groupId: String): ApiResponse<Group>
    
    @PUT("/groups/{groupId}")
    suspend fun updateGroup(
        @Path("groupId") groupId: String,
        @Body request: UpdateGroupRequest
    ): ApiResponse<Group>
    
    @POST("/groups/{groupId}/members")
    suspend fun addMember(
        @Path("groupId") groupId: String,
        @Body request: AddMemberRequest
    ): ApiResponse<GroupMember>
    
    @DELETE("/groups/{groupId}/members/{memberId}")
    suspend fun removeMember(
        @Path("groupId") groupId: String,
        @Path("memberId") memberId: String
    ): ApiResponse<Unit>
    
    @GET("/groups/{groupId}/admin-logs")
    suspend fun getAdminLogs(@Path("groupId") groupId: String): ApiResponse<List<AdminLog>>
}
```

### 3. CALLS (WebRTC)

#### WebRTC Manager: `network/WebRTCManager.kt` (57KB)

```kotlin
class WebRTCManager {
    // Инициировать звонок
    fun initiateCall(
        targetUserId: String,
        callType: CallType // AUDIO, VIDEO
    )
    
    // Ответить на звонок
    fun answerCall(callId: String)
    
    // Завершить звонок
    fun endCall(callId: String)
    
    // Переключение микрофона
    fun toggleMicrophone(enabled: Boolean)
    
    // Переключение камеры
    fun toggleCamera(enabled: Boolean)
    
    // Переключение динамика
    fun toggleSpeaker(enabled: Boolean)
    
    // Экран шеринг
    fun startScreenShare(mediaProjectionPermission: MediaProjection)
    
    // Обработчики событий
    fun onCallInitiated(listener: (Call) -> Unit)
    fun onCallAnswered(listener: (Call) -> Unit)
    fun onCallEnded(listener: (CallEndReason) -> Unit)
    fun onRemoteStreamAdded(listener: (MediaStream) -> Unit)
}
```

#### Модель: `data/model/Call.kt`

```kotlin
data class Call(
    val id: String,
    val callerId: String,
    val recipientId: String,
    val type: CallType, // AUDIO, VIDEO
    val status: CallStatus, // INITIATING, RINGING, ACTIVE, ENDED
    val startedAt: Long?,
    val endedAt: Long?,
    val duration: Long?, // milliseconds
    val endReason: CallEndReason? // COMPLETED, REJECTED, MISSED, FAILED
)

enum class CallType { AUDIO, VIDEO }
enum class CallStatus { INITIATING, RINGING, ACTIVE, ENDED }
enum class CallEndReason { COMPLETED, REJECTED, MISSED, FAILED, TIMEOUT }
```

---

## АРХИТЕКТУРА

### MVVM (Model-View-ViewModel)

```
┌─────────────────────────────────────┐
│         Compose UI Screen           │ ← View
│  (ChatsScreen, MessagesScreen, etc) │
└─────────────┬───────────────────────┘
              │
              │ observes
              ↓
┌─────────────────────────────────────┐
│  ViewModel (State Management)        │ ← ViewModel
│  (ChatsViewModel, MessagesViewModel) │
│  - manages state (MutableStateFlow)  │
│  - handles business logic            │
└─────────────┬───────────────────────┘
              │
              │ calls
              ↓
┌─────────────────────────────────────┐
│  Repository (Data Abstraction)      │ ← Repository
│  - MessageRepository                │
│  - ChatRepository                   │
│  - interfaces with local & remote   │
└─────────────┬───────────────────────┘
              │
        ┌─────┴─────┐
        ↓           ↓
┌──────────────┐ ┌─────────────────┐
│  Network     │ │ Local Database  │ ← Model
│  (API calls) │ │ (Room DB)       │
└──────────────┘ └─────────────────┘
```

### Data Flow

```
User Action (click button)
    ↓
Compose Updates State
    ↓
ViewModel.sendMessage() called
    ↓
Repository.sendMessage() called
    ↓
Network.NodeApi.sendMessage() HTTP request
    ↓
Server processes & broadcasts via WebSocket
    ↓
SocketManager receives "message:new" event
    ↓
Repository updates local database
    ↓
ViewModel updates _messages StateFlow
    ↓
Compose re-recomposes with new message
```

### State Management Example

```kotlin
// ViewModel
class MessagesViewModel(private val repo: MessageRepository) : ViewModel() {
    // UI state exposed as StateFlow
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        // Subscribe to WebSocket events
        viewModelScope.launch {
            repo.onMessageReceived().collect { message ->
                _messages.value = _messages.value + message
            }
        }
    }
    
    fun sendMessage(content: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.sendMessage(chatId, content)
                // Message will appear via WebSocket
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// Compose Screen
@Composable
fun MessagesScreen(viewModel: MessagesViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    LazyColumn {
        items(messages) { message ->
            MessageBubble(message)
        }
    }
    
    if (isLoading) {
        LoadingIndicator()
    }
}
```

---

## VIEWMODELS & STATE MANAGEMENT

### Все ViewModels в приложении:

1. **ChatsViewModel** - Управление списком чатов
2. **MessagesViewModel** - Управление сообщениями в чате
3. **GroupsViewModel** - Управление группами
4. **ChannelsViewModel** - Управление каналами
5. **CallsViewModel** - Управление звонками
6. **UserProfileViewModel** - Профиль пользователя
7. **SettingsViewModel** - Настройки приложения
8. **LoginViewModel** - Авторизация
9. **StoryViewModel** - Stories/посты
10. **SearchViewModel** - Поиск

### StateFlow vs LiveData

**Используется:** StateFlow (современный подход)

```kotlin
// StateFlow - горячий поток
private val _state = MutableStateFlow(initialState)
val state: StateFlow<State> = _state.asStateFlow()

// Функции для обновления
private fun updateState(newState: State) {
    _state.value = newState
}

// В Compose
val state by viewModel.state.collectAsState()
```

---

## NETWORK LAYER

### Retrofit Setup: `network/NodeRetrofitClient.kt`

```kotlin
object NodeRetrofitClient {
    private const val BASE_URL = "https://api.worldmates.node/"
    
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(TokenRefreshInterceptor())  // Auto refresh JWT
        .addInterceptor(NetworkInterceptor())        // Logging
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    
    val retrofitInstance: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .build()
}

// Использование
val nodeApi = NodeRetrofitClient.retrofitInstance.create(NodeApi::class.java)
```

### Request/Response Examples

**Отправить сообщение:**

```kotlin
// Request
data class SendMessageRequest(
    val content: String,
    val mediaUrls: List<String>,
    val replyToId: String?
)

// Response (через API)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String?
)

// Usage in Repository
suspend fun sendMessage(chatId: String, content: String) {
    val request = SendMessageRequest(content, emptyList(), null)
    val response = nodeApi.sendMessage(chatId, request)
    if (response.success && response.data != null) {
        // Save to local DB
        messageDao.insert(response.data.toEntity())
    }
}
```

### WebSocket (Socket.IO): `network/SocketManager.kt`

```kotlin
class SocketManager {
    private val socket = IO.socket(
        "https://api.worldmates.node",
        IO.Options().apply {
            auth = mapOf("token" to tokenProvider.getToken())
            reconnection = true
            reconnectionDelay = 1000
            reconnectionAttempts = 5
        }
    )
    
    init {
        setupListeners()
    }
    
    private fun setupListeners() {
        socket.on(Socket.EVENT_CONNECT) { onConnect() }
        socket.on(Socket.EVENT_DISCONNECT) { onDisconnect() }
        socket.on("message:new") { args -> onMessageReceived(args) }
        socket.on("typing:start") { args -> onUserTyping(args) }
        socket.on("user:online") { args -> onUserOnline(args) }
    }
    
    fun emitMessage(message: Message) {
        socket.emit("message:send", message.toJson())
    }
    
    fun notifyTyping(chatId: String, isTyping: Boolean) {
        socket.emit("typing:notify", chatId, isTyping)
    }
}
```

---

## DATABASE LAYER

### Room Database: `data/local/AppDatabase.kt`

```kotlin
@Database(
    entities = [
        MessageEntity::class,
        ChatEntity::class,
        UserEntity::class,
        GroupEntity::class,
        ChannelEntity::class,
        StoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun channelDao(): ChannelDao
    abstract fun storyDao(): StoryDao
    
    companion object {
        private var instance: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return instance ?: Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "worldmates_db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
```

### DAO Example: `data/local/dao/MessageDao.kt`

```kotlin
@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)
    
    @Update
    suspend fun update(message: MessageEntity)
    
    @Delete
    suspend fun delete(message: MessageEntity)
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getMessagesByChatPaginated(
        chatId: String,
        limit: Int,
        offset: Int
    ): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?
    
    @Query("DELETE FROM messages WHERE timestamp < :olderThanTimestamp")
    suspend fun deleteOldMessages(olderThanTimestamp: Long)
}
```

---

## UI COMPONENTS

### Jetpack Compose Architecture

**Screen Structure:**
```kotlin
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    navController: NavController
) {
    // State collection
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Content
    Scaffold(
        topBar = { MessageScreenTopBar() },
        bottomBar = { MessageInputBar(onSendClick = { viewModel.sendMessage(it) }) }
    ) { padding ->
        if (isLoading) {
            LoadingIndicator()
        } else {
            MessageList(messages, modifier = Modifier.padding(padding))
        }
    }
}
```

**Material Design 3 Integration:**
```kotlin
// Using Material3 colors and typography
Text(
    "Hello World",
    style = MaterialTheme.typography.headlineSmall,
    color = MaterialTheme.colorScheme.primary
)

Button(
    onClick = { },
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
    )
) {
    Text("Click me")
}
```

**Animations:**
```kotlin
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.95f else 1f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
)

Box(
    modifier = Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable { isPressed = !isPressed }
)
```

---

## SERVICES

### MessageNotificationService (36KB)

```kotlin
class MessageNotificationService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getParcelableExtra<Message>("message")
        val chatId = intent?.getStringExtra("chatId")
        
        // Create notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New message from ${message?.senderName}")
            .setContentText(message?.content)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent(chatId))
            .build()
        
        // Show notification
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        return START_STICKY
    }
    
    private fun createPendingIntent(chatId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "OPEN_CHAT"
            putExtra("chatId", chatId)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
```

### Firebase Messaging Service

```kotlin
class WMFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "New Message"
        val body = remoteMessage.notification?.body ?: ""
        
        val notification = NotificationCompat.Builder(this, "messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(this).notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
    
    override fun onNewToken(token: String) {
        // Send token to backend for registration
        sendTokenToServer(token)
    }
}
```

---

## SECURITY

### Encryption Example: `utils/EncryptedMediaHandler.kt`

```kotlin
object EncryptedMediaHandler {
    fun encryptFile(
        file: File,
        iv: String,
        tag: String
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = deriveKeyFromPassword(ENCRYPTION_PASSWORD)
        val ivSpec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(file.readBytes())
    }
    
    fun decryptFile(
        encryptedData: ByteArray,
        iv: String,
        tag: String
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = deriveKeyFromPassword(ENCRYPTION_PASSWORD)
        val ivSpec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(encryptedData)
    }
    
    private fun deriveKeyFromPassword(password: String): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, 0, tmp.encoded.size, "AES")
    }
}
```

### Token Management: `network/TokenRefreshInterceptor.kt`

```kotlin
class TokenRefreshInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Add authorization header
        val authenticatedRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${tokenProvider.getToken()}")
            .build()
        
        var response = chain.proceed(authenticatedRequest)
        
        // If 401, try to refresh token
        if (response.code == 401) {
            synchronized(this) {
                val newToken = tokenProvider.refreshToken()
                if (newToken != null) {
                    val retryRequest = originalRequest.newBuilder()
                        .addHeader("Authorization", "Bearer $newToken")
                        .build()
                    response.close()
                    response = chain.proceed(retryRequest)
                }
            }
        }
        
        return response
    }
}
```

---

## КАК РАСШИРЯТЬ ФУНКЦИОНАЛЬНОСТЬ

### 1. Добавить новый экран

```kotlin
// Step 1: Create data model
data class NewFeature(
    val id: String,
    val name: String,
    // ... other fields
)

// Step 2: Create ViewModel
class NewFeatureViewModel : ViewModel() {
    private val _state = MutableStateFlow<NewFeature?>(null)
    val state = _state.asStateFlow()
    
    fun loadData() {
        viewModelScope.launch {
            _state.value = repository.getNewFeature()
        }
    }
}

// Step 3: Create Compose Screen
@Composable
fun NewFeatureScreen(viewModel: NewFeatureViewModel) {
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
    
    when (state) {
        null -> LoadingIndicator()
        else -> ContentView(state!!)
    }
}

// Step 4: Add to navigation
navController.navigate("new_feature_route")
```

### 2. Добавить новый API endpoint

```kotlin
// Step 1: Update NodeApi interface
interface NodeApi {
    @GET("/new-feature/{id}")
    suspend fun getNewFeature(
        @Path("id") id: String
    ): ApiResponse<NewFeature>
}

// Step 2: Create Repository
class NewFeatureRepository(
    private val api: NodeApi,
    private val db: AppDatabase
) {
    suspend fun getNewFeature(id: String): NewFeature {
        return api.getNewFeature(id).data 
            ?: throw Exception("Failed to get feature")
    }
}

// Step 3: Use in ViewModel
class NewFeatureViewModel(
    private val repo: NewFeatureRepository
) : ViewModel() {
    fun loadFeature(id: String) {
        viewModelScope.launch {
            try {
                val feature = repo.getNewFeature(id)
                _state.value = feature
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

### 3. Добавить WebSocket событие

```kotlin
// Step 1: Define event
class FeatureUpdateEvent(
    val featureId: String,
    val newData: String
)

// Step 2: Add listener in SocketManager
fun onFeatureUpdated(listener: (FeatureUpdateEvent) -> Unit) {
    socket.on("feature:updated") { args ->
        val event = parseJson<FeatureUpdateEvent>(args[0])
        listener(event)
    }
}

// Step 3: Handle in ViewModel
init {
    viewModelScope.launch {
        socketManager.onFeatureUpdated { event ->
            // Update local state
            _state.value = _state.value?.copy(
                newData = event.newData
            )
        }
    }
}
```

### 4. Добавить новый DAO

```kotlin
@Dao
interface NewFeatureDao {
    @Insert
    suspend fun insert(entity: NewFeatureEntity)
    
    @Query("SELECT * FROM new_features WHERE id = :id")
    fun getById(id: String): Flow<NewFeatureEntity?>
    
    @Update
    suspend fun update(entity: NewFeatureEntity)
    
    @Delete
    suspend fun delete(entity: NewFeatureEntity)
}

// Add to AppDatabase
@Database(entities = [..., NewFeatureEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun newFeatureDao(): NewFeatureDao
}
```

---

## РЕСУРСЫ И ССЫЛКИ

### Key Files для быстрого понимания:
1. `ui/messages/MessagesScreen.kt` - основной экран сообщений
2. `network/NodeApi.kt` - все API endpoints
3. `network/SocketManager.kt` - real-time события
4. `ui/messages/MessagesViewModel.kt` - логика сообщений
5. `data/model/Message.kt` - модель данных

### Dependencies (`app/build.gradle`):
- Jetpack Compose - UI
- Room - Local Database
- Retrofit + OkHttp - HTTP Client
- Socket.IO - WebSocket
- Coroutines - Async operations
- Firebase - Push notifications
- Coil - Image loading

### Build & Run:
```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Run on emulator/device
./gradlew installDebug
adb shell am start -n com.worldmates.messenger/.MainActivity
```

