package com.worldmates.messenger.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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

    // Pre-compute gender labels
    val genderMale = stringResource(R.string.gender_male)
    val genderFemale = stringResource(R.string.gender_female)
    val genderOther = stringResource(R.string.gender_other)

    fun genderLabel(value: String) = when (value) {
        "male" -> genderMale
        "female" -> genderFemale
        else -> genderOther
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.edit_profile)) },
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
                        Text(stringResource(R.string.save), color = Color.White)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0084FF),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar Section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clickable { avatarPickerLauncher.launch("image/*") }
                        ) {
                            AsyncImage(
                                model = selectedAvatarUri ?: userData?.avatar
                                    ?: "https://worldmates.club/upload/photos/d-avatar.jpg",
                                contentDescription = stringResource(R.string.change_avatar),
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, Color(0xFF0084FF), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .align(Alignment.BottomEnd),
                                shape = CircleShape,
                                color = Color(0xFF0084FF)
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = stringResource(R.string.change_photo),
                                    tint = Color.White,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.tap_to_change_photo),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                if (errorMessage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color.Red,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text(stringResource(R.string.first_name)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text(stringResource(R.string.last_name)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = about,
                        onValueChange = { about = it },
                        label = { Text(stringResource(R.string.about)) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }

                item {
                    OutlinedTextField(
                        value = birthday,
                        onValueChange = { birthday = it },
                        label = { Text(stringResource(R.string.birthday)) },
                        leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.select_date))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("YYYY-MM-DD") }
                    )
                }

                item {
                    OutlinedTextField(
                        value = genderLabel(gender),
                        onValueChange = { },
                        label = { Text(stringResource(R.string.gender)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showGenderDialog = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.choose))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGenderDialog = true },
                        enabled = false,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text(stringResource(R.string.phone)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }

                item {
                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text(stringResource(R.string.website)) },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                }

                item {
                    OutlinedTextField(
                        value = working,
                        onValueChange = { working = it },
                        label = { Text(stringResource(R.string.working_place)) },
                        leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.address)) },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text(stringResource(R.string.city)) },
                        leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = school,
                        onValueChange = { school = it },
                        label = { Text(stringResource(R.string.school)) },
                        leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

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
                                .clickable {
                                    gender = value
                                    showGenderDialog = false
                                }
                                .padding(vertical = 12.dp),
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
