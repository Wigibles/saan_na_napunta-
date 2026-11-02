package com.saannapunta.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saannapunta.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    isDataLoaded: Boolean,
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chitoResId = remember {
        val id = context.resources.getIdentifier("chito_default", "drawable", context.packageName)
        if (id != 0) id else context.resources.getIdentifier("app_icon", "drawable", context.packageName)
    }

    // Gentle breathing scale animation for Chito mascot
    val infiniteTransition = rememberInfiniteTransition(label = "ChitoPulse")
    val mascotScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MascotScale"
    )

    // Progress bar animation
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progressAnim.animateTo(
            targetValue = 0.85f,
            animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
        )
    }

    LaunchedEffect(isDataLoaded) {
        // Guarantee pleasant branding presentation (~1200ms) and ensure repository finished loading
        delay(1100L)
        while (!isDataLoaded) {
            delay(100L)
        }
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
        )
        delay(150L)
        onSplashComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Mascot with soft circular glow & subtle breathing animation
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(mascotScale),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Green100.copy(alpha = 0.6f))
                )
                if (chitoResId != 0) {
                    Image(
                        painter = painterResource(id = chitoResId),
                        contentDescription = "Chito",
                        modifier = Modifier.size(120.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name with two-tone styling
            val annotatedTitle = buildAnnotatedString {
                withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.Bold)) {
                    append("Saan")
                }
                withStyle(SpanStyle(color = Green700, fontWeight = FontWeight.Bold)) {
                    append("NaPunta")
                }
            }
            Text(
                text = annotatedTitle,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "See where your money goes",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = InkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Sleek Rounded Progress Bar
            Column(
                modifier = Modifier.width(180.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progressAnim.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = Green600,
                    trackColor = Green100
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (progressAnim.value < 0.95f) "Loading your wallets..." else "Ready!",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Green700
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Privacy & Security Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceColor)
                    .border(1.dp, LineBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Green700,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "100% Offline • Private & Secure on Device",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = InkMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
