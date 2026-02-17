package com.worldmates.messenger.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.User
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp

class UserProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: UserProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(UserProfileViewModel::class.java)

        // Get user ID from intent (null = current user)
        val userId = intent.getLongExtra("user_id", -1L).takeIf { it != -1L }

        setContent {
            WorldMatesThemedApp {
                UserProfileScreen(
                    viewModel = viewModel,
                    userId = userId,
                    onBackClick = { finish() },
                    onSettingsClick = {
                        startActivity(Intent(this, com.worldmates.messenger.ui.settings.SettingsActivity::class.java))
                    },
                    onThemesClick = {
                        startActivity(Intent(this, com.worldmates.messenger.ui.settings.SettingsActivity::class.java).apply {
                            putExtra("open_screen", "theme")
                        })
                    }
                )
            }
        }

        // Load profile data
        viewModel.loadUserProfile(userId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    userId: Long?,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onThemesClick: () -> Unit = {}
) {
    val profileState by viewModel.profileState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val ratingState by viewModel.ratingState.collectAsState()
    val avatarUploadState by viewModel.avatarUploadState.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    val isOwnProfile = userId == null
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Повідомлення про завантаження аватара
    LaunchedEffect(avatarUploadState) {
        when (avatarUploadState) {
            is AvatarUploadState.Success -> {
                snackbarHostState.showSnackbar("Аватар обновлен")
                viewModel.resetAvatarUploadState()
            }
            is AvatarUploadState.Error -> {
                snackbarHostState.showSnackbar(
                    (avatarUploadState as AvatarUploadState.Error).message
                )
                viewModel.resetAvatarUploadState()
            }
            else -> {}
        }
    }

    if (isOwnProfile) {
        // Власний профіль - новий дизайн
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = profileState) {
                    is ProfileState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is ProfileState.Error -> {
                        ProfileErrorState(
                            message = state.message,
                            onRetry = { viewModel.loadUserProfile(userId) }
                        )
                    }
                    is ProfileState.Success -> {
                        MyProfileScreen(
                            user = state.user,
                            onEditClick = { showEditDialog = true },
                            onSettingsClick = onSettingsClick,
                            onThemesClick = onThemesClick,
                            onAvatarSelected = { uri ->
                                viewModel.uploadAvatar(uri, context)
                            }
                        )
                    }
                }

                // Індикатор завантаження аватара
                if (avatarUploadState is AvatarUploadState.Loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Загрузка аватара...")
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Чужий профіль - старий дизайн з TopAppBar
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Профіль") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = profileState) {
                    is ProfileState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is ProfileState.Error -> {
                        ProfileErrorState(
                            message = state.message,
                            onRetry = { viewModel.loadUserProfile(userId) }
                        )
                    }
                    is ProfileState.Success -> {
                        UserProfileContent(
                            user = state.user,
                            isOwnProfile = false,
                            ratingState = ratingState,
                            onRateUser = { ratingType, comment ->
                                viewModel.rateUser(state.user.userId, ratingType, comment)
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit Profile Dialog
    if (showEditDialog && profileState is ProfileState.Success) {
        EditProfileDialog(
            user = (profileState as ProfileState.Success).user,
            updateState = updateState,
            onDismiss = {
                showEditDialog = false
                viewModel.resetUpdateState()
            },
            onSave = { firstName, lastName, about, birthday, gender, phoneNumber, website, working, address, city, school ->
                viewModel.updateProfile(
                    firstName = firstName,
                    lastName = lastName,
                    about = about,
                    birthday = birthday,
                    gender = gender,
                    phoneNumber = phoneNumber,
                    website = website,
                    working = working,
                    address = address,
                    city = city,
                    school = school
                )
            }
        )

        // Auto-dismiss on success
        LaunchedEffect(updateState) {
            if (updateState is UpdateState.Success) {
                showEditDialog = false
                viewModel.resetUpdateState()
            }
        }
    }
}

@Composable
private fun ProfileErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Спробувати ще раз")
        }
    }
}

@Composable
fun UserProfileContent(
    user: User,
    isOwnProfile: Boolean,
    ratingState: RatingState,
    onRateUser: (String, String?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Cover and Avatar Section
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Cover photo
                AsyncImage(
                    model = user.cover ?: "https://via.placeholder.com/800x200/667eea/ffffff?text=Cover",
                    contentDescription = "Cover photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f)
                                )
                            )
                        )
                )

                // Avatar at bottom
                AsyncImage(
                    model = user.avatar,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // User Info Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Name
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
                            .ifBlank { user.username },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (user.verified == 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF0084FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Username
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Bio
                if (!user.about.isNullOrBlank()) {
                    Text(
                        text = user.about,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        // Stats Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Підписників",
                    count = user.followersCount ?: "0"
                )
                StatItem(
                    label = "Підписки",
                    count = user.followingCount ?: "0"
                )
                StatItem(
                    label = "Пости",
                    count = user.details?.postCount?.toString() ?: "0"
                )
            }
        }

        // Rating/Karma Section (only for other users' profiles)
        if (!isOwnProfile) {
            item {
                Divider(modifier = Modifier.padding(vertical = 12.dp))

                when (ratingState) {
                    is RatingState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    is RatingState.Success -> {
                        UserRatingCard(
                            rating = ratingState.rating,
                            onLikeClick = { onRateUser("like", null) },
                            onDislikeClick = { onRateUser("dislike", null) }
                        )
                    }
                    is RatingState.Error -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = ratingState.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Additional Info Section
        item {
            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Інформація",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (!user.working.isNullOrBlank()) {
                    InfoItem(
                        icon = Icons.Default.Work,
                        label = "Робота",
                        value = user.working
                    )
                }

                if (!user.school.isNullOrBlank()) {
                    InfoItem(
                        icon = Icons.Default.School,
                        label = "Навчання",
                        value = user.school
                    )
                }

                if (!user.city.isNullOrBlank()) {
                    InfoItem(
                        icon = Icons.Default.LocationOn,
                        label = "Місто",
                        value = user.city
                    )
                }

                if (!user.website.isNullOrBlank()) {
                    InfoItem(
                        icon = Icons.Default.Language,
                        label = "Веб-сайт",
                        value = user.website
                    )
                }

                if (!user.birthday.isNullOrBlank()) {
                    InfoItem(
                        icon = Icons.Default.Cake,
                        label = "День народження",
                        value = user.birthday
                    )
                }

                if (user.gender != null) {
                    InfoItem(
                        icon = Icons.Default.Person,
                        label = "Стать",
                        value = when (user.gender) {
                            "male" -> "Чоловік"
                            "female" -> "Жінка"
                            else -> user.gender
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatItem(label: String, count: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    user: User,
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?) -> Unit
) {
    var firstName by remember { mutableStateOf(user.firstName ?: "") }
    var lastName by remember { mutableStateOf(user.lastName ?: "") }
    var about by remember { mutableStateOf(user.about ?: "") }
    var birthday by remember { mutableStateOf(user.birthday ?: "") }
    var gender by remember { mutableStateOf(user.gender ?: "male") }
    var phoneNumber by remember { mutableStateOf(user.phoneNumber ?: "") }
    var website by remember { mutableStateOf(user.website ?: "") }
    var working by remember { mutableStateOf(user.working ?: "") }
    var address by remember { mutableStateOf(user.address ?: "") }
    var city by remember { mutableStateOf(user.city ?: "") }
    var school by remember { mutableStateOf(user.school ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редагувати профіль") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Ім'я") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Прізвище") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = about,
                        onValueChange = { about = it },
                        label = { Text("Про себе") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        maxLines = 3
                    )
                }

                item {
                    Text(
                        text = "Стать",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = gender == "male",
                            onClick = { gender = "male" },
                            label = { Text("Чоловік") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = gender == "female",
                            onClick = { gender = "female" },
                            label = { Text("Жінка") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = working,
                        onValueChange = { working = it },
                        label = { Text("Робота") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = school,
                        onValueChange = { school = it },
                        label = { Text("Навчання") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Місто") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text("Веб-сайт") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                if (updateState is UpdateState.Error) {
                    item {
                        Text(
                            text = updateState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        firstName.ifBlank { null },
                        lastName.ifBlank { null },
                        about.ifBlank { null },
                        birthday.ifBlank { null },
                        gender,
                        phoneNumber.ifBlank { null },
                        website.ifBlank { null },
                        working.ifBlank { null },
                        address.ifBlank { null },
                        city.ifBlank { null },
                        school.ifBlank { null }
                    )
                },
                enabled = updateState !is UpdateState.Loading
            ) {
                if (updateState is UpdateState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Зберегти")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}

/**
 * Card для відображення рейтингу користувача з можливістю оцінки
 */
@Composable
fun UserRatingCard(
    rating: com.worldmates.messenger.data.model.UserRating,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Карма користувача",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rating.trustLevelEmoji,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                // Trust level badge
                Surface(
                    color = when (rating.trustLevel) {
                        "verified" -> Color(0xFF4CAF50)
                        "trusted" -> Color(0xFF2196F3)
                        "neutral" -> Color(0xFF9E9E9E)
                        "untrusted" -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (rating.trustLevel) {
                            "verified" -> "Перевірений"
                            "trusted" -> "Надійний"
                            "neutral" -> "Нейтральний"
                            "untrusted" -> "Ненадійний"
                            else -> "Невідомо"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rating stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Likes
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = "Likes",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = rating.likes.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "Лайків",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Score
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Score",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = String.format("%.1f", rating.score),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                    Text(
                        text = "Рейтинг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Dislikes
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription = "Dislikes",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = rating.dislikes.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF44336)
                    )
                    Text(
                        text = "Дізлайків",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Text(
                text = "Оцініть цього користувача:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Like button
                Button(
                    onClick = onLikeClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rating.myRating?.type == "like") {
                            Color(0xFF4CAF50)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (rating.myRating?.type == "like") {
                            Color.White
                        } else {
                            Color(0xFF4CAF50)
                        }
                    )
                ) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = "Like",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (rating.myRating?.type == "like") "Лайк поставлено" else "Лайк"
                    )
                }

                // Dislike button
                Button(
                    onClick = onDislikeClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rating.myRating?.type == "dislike") {
                            Color(0xFFF44336)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (rating.myRating?.type == "dislike") {
                            Color.White
                        } else {
                            Color(0xFFF44336)
                        }
                    )
                ) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription = "Dislike",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (rating.myRating?.type == "dislike") "Дізлайк поставлено" else "Дізлайк"
                    )
                }
            }

            // My rating info
            rating.myRating?.let { myRating ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ви вже оцінили цього користувача. Натисніть кнопку ще раз щоб зняти оцінку.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
