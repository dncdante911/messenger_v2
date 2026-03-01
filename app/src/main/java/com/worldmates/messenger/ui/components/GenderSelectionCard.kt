package com.worldmates.messenger.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.R

/**
 * Компонент для выбора пола при регистрации
 *
 * @param gender "male" или "female"
 * @param isSelected выбран ли данный вариант
 * @param onSelect callback при выборе
 * @param avatarRes ресурс изображения аватара (опционально)
 * @param icon иконка (используется если avatarRes не указан)
 * @param label текст метки
 */
@Composable
fun GenderSelectionCard(
    gender: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    @DrawableRes avatarRes: Int? = null,
    icon: ImageVector? = null,
    label: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    // Цвета для разных полов
    val cardColor = when {
        isSelected && gender == "male" -> Color(0xFF6EC6FF).copy(alpha = 0.2f)
        isSelected && gender == "female" -> Color(0xFFFF80AB).copy(alpha = 0.2f)
        else -> colorScheme.surface
    }

    val borderColor = when {
        isSelected && gender == "male" -> Color(0xFF2196F3)
        isSelected && gender == "female" -> Color(0xFFE91E63)
        else -> Color.Transparent
    }

    Surface(
        modifier = modifier
            .size(90.dp, 106.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(2.dp, borderColor),
        tonalElevation = if (isSelected) 8.dp else 2.dp,
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Показываем аватар или иконку
            if (avatarRes != null) {
                // Если есть custom аватарка
                Image(
                    painter = painterResource(avatarRes),
                    contentDescription = label,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (icon != null) {
                // Иначе показываем иконку
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(36.dp),
                    tint = when {
                        isSelected && gender == "male" -> Color(0xFF2196F3)
                        isSelected && gender == "female" -> Color(0xFFE91E63)
                        else -> colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected && gender == "male" -> Color(0xFF2196F3)
                    isSelected && gender == "female" -> Color(0xFFE91E63)
                    else -> colorScheme.onSurface
                }
            )
        }
    }
}

/**
 * Группа выбора пола с двумя вариантами
 */
@Composable
fun GenderSelectionGroup(
    selectedGender: String,
    onGenderChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes maleAvatarRes: Int? = null,
    @DrawableRes femaleAvatarRes: Int? = null
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.gender_select_label),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Мужской
            GenderSelectionCard(
                gender = "male",
                isSelected = selectedGender == "male",
                onSelect = { onGenderChange("male") },
                avatarRes = maleAvatarRes,
                icon = Icons.Default.Man,
                label = stringResource(R.string.gender_male)
            )

            // Женский
            GenderSelectionCard(
                gender = "female",
                isSelected = selectedGender == "female",
                onSelect = { onGenderChange("female") },
                avatarRes = femaleAvatarRes,
                icon = Icons.Default.Woman,
                label = stringResource(R.string.gender_female)
            )
        }
    }
}
