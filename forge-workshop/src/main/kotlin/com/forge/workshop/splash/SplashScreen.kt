package com.forge.workshop.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.forge.workshop.nav.Spark
import com.forge.workshop.theme.ForgeTheme
import com.forge.workshop.theme.forgeColors

/** Branded startup splash — shown while the app wires up, then replaced by the main window. */
@Composable
fun SplashWindow() {
    val state = rememberWindowState(size = DpSize(440.dp, 260.dp), position = WindowPosition(Alignment.Center))
    Window(
        onCloseRequest = {},
        state = state,
        title = "The Forge",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        ForgeTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(forgeColors.ground)
                    .border(1.dp, forgeColors.border, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spark(56.dp)
                    Spacer(Modifier.height(18.dp))
                    Text("The Forge", color = forgeColors.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text("инженерная кузница", color = forgeColors.inkFaint, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Spacer(Modifier.height(26.dp))
                    LoaderBar()
                    Spacer(Modifier.height(12.dp))
                    Text("Разогреваем горн…", color = forgeColors.inkMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Indeterminate ember bar sliding back and forth along a dark track. */
@Composable
private fun LoaderBar() {
    val transition = rememberInfiniteTransition()
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )
    Box(
        modifier = Modifier.width(220.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(forgeColors.surface2),
    ) {
        Box(
            modifier = Modifier
                .offset(x = (x * 156f).dp)
                .width(64.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(forgeColors.emberHot, forgeColors.ember))),
        )
    }
}
