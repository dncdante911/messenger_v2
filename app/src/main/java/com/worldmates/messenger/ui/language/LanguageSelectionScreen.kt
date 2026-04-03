package com.worldmates.messenger.ui.language

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.login.LoginActivity
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * Activity для вибору мови при першому запуску.
 * Показується один раз — після вибору переходить до LoginActivity.
 */
class LanguageSelectionActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            WorldMatesThemedApp {
                LanguageSelectionScreen(
                    onLanguageSelected = { lang ->
                        LanguageManager.setLanguage(lang)
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

/**
 * Composable для вибору мови (першочерговий запуск або з налаштувань).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (String) -> Unit,
    currentLanguage: String = LanguageManager.currentLanguage,
    onBackClick: (() -> Unit)? = null
) {
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            if (onBackClick != null) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.language_settings_title),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surface,
                        titleContentColor = colorScheme.onSurface
                    )
                )
            }
        },
        containerColor = colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Для первого запуска (без back) показываем заголовок по центру
            if (onBackClick == null) {
                Spacer(modifier = Modifier.weight(0.15f))

                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Language,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.language_selection_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.language_selection_subtitle),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(36.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.language_selection_subtitle),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Language options
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LanguageOption(
                        flag = "\uD83C\uDDFA\uD83C\uDDE6",
                        name = stringResource(R.string.language_ukrainian),
                        isSelected = selectedLanguage == LanguageManager.LANG_UK,
                        onClick = { selectedLanguage = LanguageManager.LANG_UK }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    LanguageOption(
                        flag = "\uD83C\uDDF7\uD83C\uDDFA",
                        name = stringResource(R.string.language_russian),
                        isSelected = selectedLanguage == LanguageManager.LANG_RU,
                        onClick = { selectedLanguage = LanguageManager.LANG_RU }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue / Apply button
            Button(
                onClick = { onLanguageSelected(selectedLanguage) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary
                )
            ) {
                Text(
                    text = if (onBackClick != null) stringResource(R.string.language_select)
                           else stringResource(R.string.language_continue),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (onBackClick == null) {
                Spacer(modifier = Modifier.weight(0.25f))
            }
        }
    }
}

@Composable
private fun LanguageOption(
    flag: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = flag,
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = name,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(
                        2.dp,
                        colorScheme.outlineVariant,
                        CircleShape
                    )
            )
        }
    }
}
