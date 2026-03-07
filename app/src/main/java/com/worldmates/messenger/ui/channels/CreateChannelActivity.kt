package com.worldmates.messenger.ui.channels

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

class CreateChannelActivity : AppCompatActivity() {

    private lateinit var viewModel: ChannelsViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

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
                        Toast.makeText(
                            this,
                            getString(R.string.channel_created_format, channel.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                )
            }
        }
    }
}

// ==================== PREMIUM CREATE CHANNEL SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChannelScreen(
    viewModel: ChannelsViewModel,
    onBackPressed: () -> Unit,
    onChannelCreated: (com.worldmates.messenger.data.model.Channel) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    // Channel data
    var channelType by remember { mutableStateOf("public") }
    var channelName by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("") }
    var channelUsername by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val isCreating by viewModel.isCreatingChannel.collectAsState()
    val context = LocalContext.current

    val premiumGradient = Brush.horizontalGradient(
        colors = listOf(
            PremiumColors.GradientStart,
            PremiumColors.GradientMiddle,
            PremiumColors.GradientEnd
        )
    )

    Scaffold(
        topBar = {
            Column {
                // Premium gradient top bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(premiumGradient)
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (currentStep > 0) currentStep-- else onBackPressed()
                        }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.new_channel),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (currentStep) {
                                    0 -> stringResource(R.string.channel_step_type)
                                    1 -> stringResource(R.string.channel_step_info)
                                    2 -> stringResource(R.string.channel_avatar)
                                    else -> stringResource(R.string.channel_step_final)
                                },
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                        // Step counter badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = "${currentStep + 1}/$totalSteps",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Step progress indicator
                StepProgressBar(
                    currentStep = currentStep,
                    totalSteps = totalSteps
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "step_animation"
        ) { step ->
            when (step) {
                0 -> PremiumChannelTypeStep(
                    selectedType = channelType,
                    onTypeSelected = { type ->
                        channelType = type
                        currentStep = 1
                    }
                )
                1 -> PremiumChannelInfoStep(
                    channelType = channelType,
                    channelName = channelName,
                    onNameChange = { channelName = it },
                    channelDescription = channelDescription,
                    onDescriptionChange = { channelDescription = it },
                    channelUsername = channelUsername,
                    onUsernameChange = { channelUsername = it },
                    onNext = { currentStep = 2 }
                )
                2 -> PremiumChannelAvatarStep(
                    channelName = channelName,
                    selectedUri = selectedAvatarUri,
                    onUriSelected = { selectedAvatarUri = it },
                    onSkip = { currentStep = 3 },
                    onNext = { currentStep = 3 }
                )
                3 -> PremiumChannelSettingsStep(
                    channelName = channelName,
                    channelType = channelType,
                    isCreating = isCreating,
                    onCreate = {
                        viewModel.createChannel(
                            name = channelName,
                            description = channelDescription,
                            username = if (channelType == "public") channelUsername else null,
                            isPrivate = channelType == "private",
                            onSuccess = { channel -> onChannelCreated(channel) },
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

// ==================== STEP PROGRESS BAR ====================

@Composable
fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue = (currentStep + 1).toFloat() / totalSteps,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            PremiumColors.GradientStart,
                            PremiumColors.GradientEnd
                        )
                    )
                )
        )
    }
}

// ==================== STEP 1: CHANNEL TYPE ====================

@Composable
fun PremiumChannelTypeStep(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header illustration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                PremiumColors.GradientStart.copy(alpha = 0.15f),
                                PremiumColors.GradientEnd.copy(alpha = 0.15f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Podcasts,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = PremiumColors.GradientStart
                )
            }
        }

        Text(
            text = stringResource(R.string.channel_type_question),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.channel_type_subtitle),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Public channel card
        PremiumTypeCard(
            icon = Icons.Outlined.Public,
            title = stringResource(R.string.public_channel_title),
            description = stringResource(R.string.public_channel_desc),
            accentColor = PremiumColors.TelegramBlue,
            gradientColors = listOf(Color(0xFF0088CC), Color(0xFF54A9EB)),
            isSelected = selectedType == "public",
            onClick = { onTypeSelected("public") }
        )

        // Private channel card
        PremiumTypeCard(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.private_channel_title),
            description = stringResource(R.string.private_channel_desc),
            accentColor = PremiumColors.GradientMiddle,
            gradientColors = listOf(Color(0xFF764ba2), Color(0xFFf093fb)),
            isSelected = selectedType == "private",
            onClick = { onTypeSelected("private") }
        )
    }
}

@Composable
fun PremiumTypeCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    gradientColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300),
        label = "border_alpha"
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        animationSpec = tween(300),
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(16.dp),
                ambientColor = accentColor.copy(alpha = 0.2f),
                spotColor = accentColor.copy(alpha = 0.2f)
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    brush = Brush.linearGradient(gradientColors),
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                accentColor.copy(alpha = 0.06f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with gradient background
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isSelected) gradientColors
                            else listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // Selection indicator
            AnimatedVisibility(visible = isSelected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ==================== STEP 2: CHANNEL INFO ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumChannelInfoStep(
    channelType: String,
    channelName: String,
    onNameChange: (String) -> Unit,
    channelDescription: String,
    onDescriptionChange: (String) -> Unit,
    channelUsername: String,
    onUsernameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val isValid = channelName.isNotBlank() &&
            (channelType == "private" || channelUsername.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.channel_tell_us),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.channel_info_visible),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Channel Name Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.DriveFileRenameOutline,
                        contentDescription = null,
                        tint = PremiumColors.GradientStart,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.channel_name),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumColors.GradientStart
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = channelName,
                    onValueChange = onNameChange,
                    placeholder = { Text(stringResource(R.string.channel_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PremiumColors.GradientStart,
                        cursorColor = PremiumColors.GradientStart
                    )
                )
            }
        }

        // Description Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = PremiumColors.GradientMiddle,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.channel_description),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumColors.GradientMiddle
                    )
                    Text(
                        stringResource(R.string.optional_label),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = channelDescription,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text(stringResource(R.string.channel_desc_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PremiumColors.GradientMiddle,
                        cursorColor = PremiumColors.GradientMiddle
                    )
                )
                Text(
                    text = "${channelDescription.length}/500",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }

        // Username Card (public only)
        if (channelType == "public") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AlternateEmail,
                            contentDescription = null,
                            tint = PremiumColors.TelegramBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.username),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PremiumColors.TelegramBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = channelUsername,
                        onValueChange = { value ->
                            val filtered = value.filter { it.isLetterOrDigit() || it == '_' }
                            onUsernameChange(filtered)
                        },
                        placeholder = { Text("my_channel") }, // username format stays the same
                        prefix = {
                            Text(
                                "@",
                                color = PremiumColors.TelegramBlue,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PremiumColors.TelegramBlue,
                            cursorColor = PremiumColors.TelegramBlue
                        )
                    )
                    Text(
                        text = stringResource(R.string.username_rules),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Next button
        PremiumGradientButton(
            text = stringResource(R.string.continue_btn),
            enabled = isValid,
            onClick = onNext
        )
    }
}

// ==================== STEP 3: AVATAR ====================

@Composable
fun PremiumChannelAvatarStep(
    channelName: String,
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

    val infiniteTransition = rememberInfiniteTransition(label = "ring_rotation")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing)
        ),
        label = "ring_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.channel_avatar_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.channel_avatar_subtitle),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Avatar with animated gradient ring
        Box(
            modifier = Modifier.size(170.dp),
            contentAlignment = Alignment.Center
        ) {
            // Animated gradient ring
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .graphicsLayer { rotationZ = ringRotation }
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            colors = listOf(
                                PremiumColors.GradientStart,
                                PremiumColors.GradientMiddle,
                                PremiumColors.GradientEnd,
                                PremiumColors.GradientStart
                            )
                        )
                    )
            )

            // Inner circle (avatar area)
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { launcher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedUri != null) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "Channel avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Show channel initials as placeholder
                        if (channelName.isNotBlank()) {
                            Text(
                                text = channelName
                                    .split(" ")
                                    .take(2)
                                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                                    .joinToString(""),
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.GradientStart
                            )
                        } else {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = PremiumColors.GradientStart.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.tap_to_select),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Camera badge overlay
            if (selectedUri != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-8).dp, y = (-8).dp)
                        .size(40.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    PremiumColors.GradientStart,
                                    PremiumColors.GradientMiddle
                                )
                            )
                        )
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.change_photo),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Skip button
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Text(stringResource(R.string.skip_for_now), fontSize = 15.sp)
            }

            // Continue button
            Button(
                onClick = onNext,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    PremiumColors.GradientStart,
                                    PremiumColors.GradientMiddle
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.continue_btn),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ==================== STEP 4: SETTINGS ====================

@Composable
fun PremiumChannelSettingsStep(
    channelName: String,
    channelType: String,
    isCreating: Boolean,
    onCreate: () -> Unit
) {
    var allowComments by remember { mutableStateOf(true) }
    var allowReactions by remember { mutableStateOf(true) }
    var showStatistics by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.channel_final_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.channel_final_subtitle),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Summary card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = PremiumColors.GradientStart.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(PremiumColors.GradientStart, PremiumColors.GradientMiddle)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channelName
                            .split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString("")
                            .ifEmpty { "#" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = channelName.ifBlank { stringResource(R.string.channel_name) },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (channelType == "public") stringResource(R.string.public_channel_title) else stringResource(R.string.private_channel_title),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Settings
        PremiumSettingsToggle(
            icon = Icons.Outlined.ChatBubbleOutline,
            title = stringResource(R.string.allow_comments),
            description = stringResource(R.string.allow_comments_desc),
            checked = allowComments,
            accentColor = PremiumColors.TelegramBlue,
            onCheckedChange = { allowComments = it }
        )

        PremiumSettingsToggle(
            icon = Icons.Outlined.EmojiEmotions,
            title = stringResource(R.string.allow_reactions),
            description = stringResource(R.string.allow_reactions_desc),
            checked = allowReactions,
            accentColor = PremiumColors.WarningOrange,
            onCheckedChange = { allowReactions = it }
        )

        PremiumSettingsToggle(
            icon = Icons.Outlined.BarChart,
            title = stringResource(R.string.show_statistics),
            description = stringResource(R.string.show_statistics_desc),
            checked = showStatistics,
            accentColor = PremiumColors.SuccessGreen,
            onCheckedChange = { showStatistics = it }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Create button
        Button(
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isCreating,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (!isCreating) Brush.horizontalGradient(
                            colors = listOf(
                                PremiumColors.SuccessGreen,
                                Color(0xFF00E676)
                            )
                        ) else Brush.horizontalGradient(
                            colors = listOf(
                                Color.Gray.copy(alpha = 0.5f),
                                Color.Gray.copy(alpha = 0.5f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.creating_channel),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            Icons.Outlined.RocketLaunch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.create_channel),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ==================== REUSABLE COMPONENTS ====================

@Composable
fun PremiumSettingsToggle(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accentColor,
                    checkedThumbColor = Color.White
                )
            )
        }
    }
}

@Composable
fun PremiumGradientButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled) Brush.horizontalGradient(
                        colors = listOf(
                            PremiumColors.GradientStart,
                            PremiumColors.GradientMiddle
                        )
                    ) else Brush.horizontalGradient(
                        colors = listOf(
                            Color.Gray.copy(alpha = 0.4f),
                            Color.Gray.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
