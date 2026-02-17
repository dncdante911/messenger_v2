package com.worldmates.messenger.ui.channels

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp

/**
 * Активність для створення нового каналу (як у Telegram)
 * Крок 1: Тип каналу (публічний/приватний)
 * Крок 2: Назва та опис
 * Крок 3: Аватар
 * Крок 4: Налаштування
 */
class CreateChannelActivity : AppCompatActivity() {

    private lateinit var viewModel: ChannelsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager.initialize(this)
        viewModel = ViewModelProvider(this).get(ChannelsViewModel::class.java)

        setContent {
            WorldMatesThemedApp {
                CreateChannelScreen(
                    viewModel = viewModel,
                    onBackPressed = { finish() },
                    onChannelCreated = { channel ->
                        // Повертаємось до головного екрану
                        Toast.makeText(
                            this,
                            "Канал \"${channel.name}\" створено!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChannelScreen(
    viewModel: ChannelsViewModel,
    onBackPressed: () -> Unit,
    onChannelCreated: (com.worldmates.messenger.data.model.Channel) -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }

    // Channel data
    var channelType by remember { mutableStateOf("public") } // "public" or "private"
    var channelName by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("") }
    var channelUsername by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val isCreating by viewModel.isCreatingChannel.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentStep) {
                            0 -> "Новий канал"
                            1 -> "Інформація про канал"
                            2 -> "Аватар каналу"
                            else -> "Налаштування"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 0) {
                            currentStep--
                        } else {
                            onBackPressed()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF667eea),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F7FA))
        ) {
            when (currentStep) {
                0 -> ChannelTypeStep(
                    selectedType = channelType,
                    onTypeSelected = { type ->
                        channelType = type
                        currentStep = 1
                    }
                )

                1 -> ChannelInfoStep(
                    channelType = channelType,
                    channelName = channelName,
                    onNameChange = { channelName = it },
                    channelDescription = channelDescription,
                    onDescriptionChange = { channelDescription = it },
                    channelUsername = channelUsername,
                    onUsernameChange = { channelUsername = it },
                    onNext = { currentStep = 2 }
                )

                2 -> ChannelAvatarStep(
                    selectedUri = selectedAvatarUri,
                    onUriSelected = { selectedAvatarUri = it },
                    onSkip = { currentStep = 3 },
                    onNext = { currentStep = 3 }
                )

                3 -> ChannelSettingsStep(
                    isCreating = isCreating,
                    onCreate = {
                        // Створюємо канал
                        viewModel.createChannel(
                            name = channelName,
                            description = channelDescription,
                            username = if (channelType == "public") channelUsername else null,
                            isPrivate = channelType == "private",
                            onSuccess = { channel ->
                                onChannelCreated(channel)
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                )
            }
        }
    }
}

/**
 * Крок 1: Вибір типу каналу
 */
@Composable
fun ChannelTypeStep(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Який тип каналу ви хочете створити?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Публічний канал
        ChannelTypeCard(
            title = "📢 Публічний канал",
            description = "Будь-хто може знайти та підписатися на ваш канал. Має посилання та username.",
            isSelected = selectedType == "public",
            onClick = { onTypeSelected("public") }
        )

        // Приватний канал
        ChannelTypeCard(
            title = "🔒 Приватний канал",
            description = "Приєднатися можна тільки за запрошенням. Не відображається у пошуку.",
            isSelected = selectedType == "private",
            onClick = { onTypeSelected("private") }
        )
    }
}

@Composable
fun ChannelTypeCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF667eea) else Color(0xFFE0E0E0)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Крок 2: Назва та опис каналу
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelInfoStep(
    channelType: String,
    channelName: String,
    onNameChange: (String) -> Unit,
    channelDescription: String,
    onDescriptionChange: (String) -> Unit,
    channelUsername: String,
    onUsernameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Інформація про канал",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // Назва каналу
        OutlinedTextField(
            value = channelName,
            onValueChange = onNameChange,
            label = { Text("Назва каналу") },
            placeholder = { Text("Мій крутий канал") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Label, contentDescription = null)
            }
        )

        // Опис
        OutlinedTextField(
            value = channelDescription,
            onValueChange = onDescriptionChange,
            label = { Text("Опис (опціонально)") },
            placeholder = { Text("Коротко опишіть ваш канал...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 4
        )

        // Username (тільки для публічних)
        if (channelType == "public") {
            OutlinedTextField(
                value = channelUsername,
                onValueChange = { value ->
                    // Дозволяємо тільки a-z, 0-9, _
                    val filtered = value.filter { it.isLetterOrDigit() || it == '_' }
                    onUsernameChange(filtered)
                },
                label = { Text("Username (без @)") },
                placeholder = { Text("my_channel") },
                prefix = { Text("@") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("Тільки латиниця, цифри та підкреслення")
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Кнопка далі
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = channelName.isNotBlank() &&
                     (channelType == "private" || channelUsername.isNotBlank()),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF667eea)
            )
        ) {
            Text("Далі")
        }
    }
}

/**
 * Крок 3: Вибір аватара
 */
@Composable
fun ChannelAvatarStep(
    selectedUri: Uri?,
    onUriSelected: (Uri?) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onUriSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Додати аватар каналу",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Аватар preview
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0))
                .clickable { launcher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (selectedUri != null) {
                AsyncImage(
                    model = selectedUri,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = "Add photo",
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { launcher.launch("image/*") }) {
            Text(if (selectedUri == null) "Вибрати фото" else "Змінити фото")
        }

        Spacer(modifier = Modifier.weight(1f))

        // Кнопки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Пропустити")
            }

            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF667eea)
                )
            ) {
                Text("Далі")
            }
        }
    }
}

/**
 * Крок 4: Фінальні налаштування та створення
 */
@Composable
fun ChannelSettingsStep(
    isCreating: Boolean,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Налаштування каналу",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        var allowComments by remember { mutableStateOf(true) }
        var allowReactions by remember { mutableStateOf(true) }
        var showStatistics by remember { mutableStateOf(true) }

        // Налаштування
        SettingsSwitchItem(
            title = "Дозволити коментарі",
            description = "Підписники зможуть коментувати пости",
            checked = allowComments,
            onCheckedChange = { allowComments = it }
        )

        SettingsSwitchItem(
            title = "Дозволити реакції",
            description = "Підписники зможуть ставити реакції на пости",
            checked = allowReactions,
            onCheckedChange = { allowReactions = it }
        )

        SettingsSwitchItem(
            title = "Показувати статистику",
            description = "Відображати перегляди та статистику",
            checked = showStatistics,
            onCheckedChange = { showStatistics = it }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Кнопка створення
        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isCreating,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            if (isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isCreating) "Створення..." else "✓ Створити канал")
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
