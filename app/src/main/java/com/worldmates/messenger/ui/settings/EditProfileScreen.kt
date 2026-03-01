package com.worldmates.messenger.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var firstName by remember { mutableStateOf(userData?.firstName ?: "") }
    var lastName by remember { mutableStateOf(userData?.lastName ?: "") }
    var about by remember { mutableStateOf(userData?.about ?: "") }
    var birthday by remember { mutableStateOf(userData?.birthday ?: "") }
    var gender by remember { mutableStateOf(userData?.gender ?: "male") }
    var phoneNumber by remember { mutableStateOf(userData?.phoneNumber ?: "") }
    var website by remember { mutableStateOf(userData?.website ?: "") }
    var working by remember { mutableStateOf(userData?.working ?: "") }
    var address by remember { mutableStateOf(userData?.address ?: "") }
    var city by remember { mutableStateOf(userData?.city ?: "") }
    var school by remember { mutableStateOf(userData?.school ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAvatarUri = it
            viewModel.uploadAvatar(it)
        }
    }

    LaunchedEffect(userData) {
        userData?.let { user ->
            firstName = user.firstName ?: ""
            lastName = user.lastName ?: ""
            about = user.about ?: ""
            birthday = user.birthday ?: ""
            gender = user.gender ?: "male"
            phoneNumber = user.phoneNumber ?: ""
            website = user.website ?: ""
            working = user.working ?: ""
            address = user.address ?: ""
            city = user.city ?: ""
            school = user.school ?: ""
        }
    }

    val genderMale = stringResource(R.string.gender_male)
    val genderFemale = stringResource(R.string.gender_female)
    val genderOther = stringResource(R.string.gender_other)

    fun genderLabel(value: String) = when (value) {
        "male" -> genderMale
        "female" -> genderFemale
        else -> genderOther
    }

    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = { Text(stringResource(R.string.edit_profile), fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (!isLoading) {
                    TextButton(
                        onClick = {
                            viewModel.updateUserProfile(
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
                    ) {
                        Text(
                            stringResource(R.string.save),
                            color = colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.primary,
                titleContentColor = colorScheme.onPrimary,
                navigationIconContentColor = colorScheme.onPrimary
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Success message
                if (successMessage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(text = successMessage ?: "", color = colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                // Error message
                if (errorMessage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(text = errorMessage ?: "", color = colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // ===== AVATAR =====
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clickable { avatarPickerLauncher.launch("image/*") }
                            ) {
                                AsyncImage(
                                    model = selectedAvatarUri ?: userData?.avatar
                                        ?: "https://worldmates.club/upload/photos/d-avatar.jpg",
                                    contentDescription = stringResource(R.string.change_avatar),
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    modifier = Modifier.size(36.dp).align(Alignment.BottomEnd),
                                    shape = CircleShape,
                                    color = colorScheme.primary,
                                    shadowElevation = 2.dp
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = stringResource(R.string.change_photo),
                                        tint = colorScheme.onPrimary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tap_to_change_photo),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            userData?.username?.let { username ->
                                Text(
                                    text = "@$username",
                                    fontSize = 14.sp,
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ===== PERSONAL INFO =====
                item {
                    ProfileSectionHeader(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.section_personal_info),
                        color = colorScheme.primary
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text(stringResource(R.string.first_name)) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it },
                                label = { Text(stringResource(R.string.last_name)) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = about,
                                onValueChange = { if (it.length <= 200) about = it },
                                label = { Text(stringResource(R.string.about)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = colorScheme.primary) },
                                supportingText = {
                                    Text(
                                        "${about.length}/200",
                                        color = if (about.length > 180) colorScheme.error else colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = birthday,
                                onValueChange = { birthday = it },
                                label = { Text(stringResource(R.string.birthday)) },
                                leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null, tint = colorScheme.primary) },
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.select_date))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("YYYY-MM-DD") },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = genderLabel(gender),
                                onValueChange = { },
                                label = { Text(stringResource(R.string.gender)) },
                                leadingIcon = {
                                    Icon(
                                        if (gender == "female") Icons.Default.Female else Icons.Default.Male,
                                        contentDescription = null,
                                        tint = colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showGenderDialog = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.choose))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable { showGenderDialog = true },
                                enabled = false,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = colorScheme.onSurface,
                                    disabledBorderColor = colorScheme.outline,
                                    disabledLeadingIconColor = colorScheme.primary,
                                    disabledTrailingIconColor = colorScheme.onSurfaceVariant,
                                    disabledLabelColor = colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // ===== CONTACT =====
                item {
                    ProfileSectionHeader(
                        icon = Icons.Default.ContactPhone,
                        title = stringResource(R.string.section_contact),
                        color = colorScheme.primary
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text(stringResource(R.string.phone)) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = website,
                                onValueChange = { website = it },
                                label = { Text(stringResource(R.string.website)) },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // ===== LOCATION =====
                item {
                    ProfileSectionHeader(
                        icon = Icons.Default.LocationOn,
                        title = stringResource(R.string.section_location),
                        color = colorScheme.primary
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text(stringResource(R.string.city)) },
                                leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null, tint = colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text(stringResource(R.string.address)) },
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // ===== WORK & EDUCATION =====
                item {
                    ProfileSectionHeader(
                        icon = Icons.Default.Work,
                        title = stringResource(R.string.section_work_education),
                        color = colorScheme.primary
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = working,
                                onValueChange = { working = it },
                                label = { Text(stringResource(R.string.working_place)) },
                                leadingIcon = { Icon(Icons.Default.Work, contentDescription = null, tint = colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = school,
                                onValueChange = { school = it },
                                label = { Text(stringResource(R.string.school)) },
                                leadingIcon = { Icon(Icons.Default.School, contentDescription = null, tint = colorScheme.primary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Gender Dialog
    if (showGenderDialog) {
        val genderOptions = listOf(
            "male" to genderMale,
            "female" to genderFemale,
            "other" to genderOther
        )
        AlertDialog(
            onDismissRequest = { showGenderDialog = false },
            title = { Text(stringResource(R.string.select_gender)) },
            text = {
                Column {
                    genderOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    gender = value
                                    showGenderDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = gender == value,
                                onClick = {
                                    gender = value
                                    showGenderDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGenderDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun ProfileSectionHeader(
    icon: ImageVector,
    title: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
