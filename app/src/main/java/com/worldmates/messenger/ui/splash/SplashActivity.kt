package com.worldmates.messenger.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.login.LoginActivity
import kotlinx.coroutines.delay

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    // Анимация масштаба иконки — spring bounce
    val iconScale = remember { Animatable(0.3f) }
    // Анимация прозрачности текста
    val textAlpha = remember { Animatable(0f) }
    // Анимация прозрачности точек
    val dotsAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Иконка "выпрыгивает" с bounce-эффектом
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
        // 2. Название появляется
        textAlpha.animateTo(1f, animationSpec = tween(400))
        // 3. Точки загрузки появляются
        dotsAlpha.animateTo(1f, animationSpec = tween(300))
        // 4. Держим экран ~1.5 сек после появления всего
        delay(1500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4ECDC4), // teal
                        Color(0xFF7DD8A0), // mid
                        Color(0xFF95E1A3)  // mint green
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Иконка приложения (маскот) с bounce-анимацией
            // После добавления реального PNG маскота замени на Image(painterResource(R.drawable.mascot_splash))
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(iconScale.value),
                contentAlignment = Alignment.Center
            ) {
                // TODO: замени на реальное изображение маскота:
                // Image(
                //     painter = painterResource(R.drawable.mascot_splash),
                //     contentDescription = "WallyMates Mascot",
                //     modifier = Modifier.fillMaxSize()
                // )
                MascotPlaceholder()
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Название приложения
            Text(
                text = "WallyMates",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Text(
                text = "Messenger",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Анимированные точки загрузки
            Box(modifier = Modifier.alpha(dotsAlpha.value)) {
                BouncingDots()
            }
        }
    }
}

/** Три точки, которые поочерёдно "подпрыгивают" */
@Composable
fun BouncingDots(
    color: Color = Color.White,
    dotSize: androidx.compose.ui.unit.Dp = 10.dp,
    spacing: androidx.compose.ui.unit.Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    val offsets = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 900
                    0f   at (index * 150)      with EaseInOut
                    -14f at (index * 150 + 200) with EaseInOut
                    0f   at (index * 150 + 400)
                    0f   at 900
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_$index"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        offsets.forEach { offset ->
            Box(
                modifier = Modifier
                    .offset(y = offset.value.dp)
                    .size(dotSize)
                    .background(color, shape = androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

/** Placeholder пока нет реальной иконки маскота */
@Composable
private fun MascotPlaceholder() {
    Box(
        modifier = Modifier
            .size(160.dp)
            .background(
                Color.White.copy(alpha = 0.25f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(40.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🐶",
            fontSize = 80.sp
        )
    }
}
