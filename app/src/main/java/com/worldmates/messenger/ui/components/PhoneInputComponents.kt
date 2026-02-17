package com.worldmates.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber

/**
 * Данные о стране
 */
data class Country(
    val name: String,
    val code: String,  // UA, US, etc.
    val dialCode: String,  // +380, +1, etc.
    val flag: String  // Emoji флаг
)

/**
 * Список популярных стран
 */
val popularCountries = listOf(
    Country("Україна", "UA", "+380", "🇺🇦"),
    Country("Росія", "RU", "+7", "🇷🇺"),
    Country("США", "US", "+1", "🇺🇸"),
    Country("Велика Британія", "GB", "+44", "🇬🇧"),
    Country("Німеччина", "DE", "+49", "🇩🇪"),
    Country("Франція", "FR", "+33", "🇫🇷"),
    Country("Італія", "IT", "+39", "🇮🇹"),
    Country("Іспанія", "ES", "+34", "🇪🇸"),
    Country("Польща", "PL", "+48", "🇵🇱"),
    Country("Туреччина", "TR", "+90", "🇹🇷"),
    Country("Китай", "CN", "+86", "🇨🇳"),
    Country("Індія", "IN", "+91", "🇮🇳"),
    Country("Японія", "JP", "+81", "🇯🇵"),
    Country("Бразилія", "BR", "+55", "🇧🇷"),
    Country("Канада", "CA", "+1", "🇨🇦"),
    Country("Австралія", "AU", "+61", "🇦🇺"),
    Country("Мексика", "MX", "+52", "🇲🇽"),
    Country("Аргентина", "AR", "+54", "🇦🇷"),
    Country("Південна Корея", "KR", "+82", "🇰🇷"),
    Country("Саудівська Аравія", "SA", "+966", "🇸🇦"),
    Country("ОАЕ", "AE", "+971", "🇦🇪"),
    Country("Ізраїль", "IL", "+972", "🇮🇱"),
    Country("Єгипет", "EG", "+20", "🇪🇬"),
    Country("ПАР", "ZA", "+27", "🇿🇦"),
    Country("Казахстан", "KZ", "+7", "🇰🇿"),
    Country("Білорусь", "BY", "+375", "🇧🇾"),
    Country("Грузія", "GE", "+995", "🇬🇪"),
    Country("Азербайджан", "AZ", "+994", "🇦🇿"),
    Country("Вірменія", "AM", "+374", "🇦🇲"),
    Country("Молдова", "MD", "+373", "🇲🇩")
)

/**
 * Компонент для ввода телефонного номера с выбором страны
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneInputField(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    selectedCountry: Country,
    onCountryChange: (Country) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    label: String = "Номер телефону"
) {
    var showCountryPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = { Text(label) },
            leadingIcon = {
                // Флаг и код страны
                Row(
                    modifier = Modifier
                        .clickable(enabled = enabled) { showCountryPicker = true }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedCountry.flag,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedCountry.dialCode,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Выбрать страну",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingIcon = {
                if (phoneNumber.isNotEmpty()) {
                    IconButton(onClick = { onPhoneNumberChange("") }) {
                        Icon(Icons.Default.Close, "Очистить")
                    }
                } else {
                    Icon(Icons.Default.Phone, "Телефон")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        // Сообщение об ошибке
        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        // Валидация номера
        if (phoneNumber.isNotEmpty()) {
            val validation = validatePhoneNumber(selectedCountry.dialCode + phoneNumber)
            if (!validation.first && !isError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = validation.second,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }

    // Диалог выбора страны
    if (showCountryPicker) {
        CountryPickerDialog(
            onDismiss = { showCountryPicker = false },
            onCountrySelected = { country ->
                onCountryChange(country)
                showCountryPicker = false
            },
            selectedCountry = selectedCountry
        )
    }
}

/**
 * Диалог выбора страны
 */
@Composable
fun CountryPickerDialog(
    onDismiss: () -> Unit,
    onCountrySelected: (Country) -> Unit,
    selectedCountry: Country
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            popularCountries
        } else {
            popularCountries.filter { country ->
                country.name.contains(searchQuery, ignoreCase = true) ||
                country.dialCode.contains(searchQuery) ||
                country.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Заголовок
                Text(
                    text = "Виберіть країну",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Поиск
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Пошук країни...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Пошук")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "Очистити")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Список стран
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCountries) { country ->
                        CountryItem(
                            country = country,
                            isSelected = country == selectedCountry,
                            onClick = { onCountrySelected(country) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Кнопка закрытия
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Скасувати")
                }
            }
        }
    }
}

/**
 * Элемент списка стран
 */
@Composable
fun CountryItem(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = country.flag,
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = country.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = country.dialCode,
                    fontSize = 12.sp,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Валидация телефонного номера
 */
fun validatePhoneNumber(fullNumber: String): Pair<Boolean, String> {
    return try {
        val phoneUtil = PhoneNumberUtil.getInstance()
        val number = phoneUtil.parse(fullNumber, null)

        if (phoneUtil.isValidNumber(number)) {
            Pair(true, "")
        } else {
            Pair(false, "Невірний формат номеру")
        }
    } catch (e: NumberParseException) {
        Pair(false, "Невірний формат номеру")
    } catch (e: Exception) {
        Pair(false, "Помилка перевірки номеру")
    }
}

/**
 * Форматирование телефонного номера
 */
fun formatPhoneNumber(fullNumber: String): String {
    return try {
        val phoneUtil = PhoneNumberUtil.getInstance()
        val number = phoneUtil.parse(fullNumber, null)
        phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: Exception) {
        fullNumber
    }
}

/**
 * Получение полного номера телефона
 */
fun getFullPhoneNumber(dialCode: String, phoneNumber: String): String {
    val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
    return "$dialCode$cleanNumber"
}
