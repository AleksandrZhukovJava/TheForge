package com.forge.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.forge.workshop.theme.ForgeTheme
import com.forge.workshop.theme.forgeColors

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "The Forge — Workshop") {
        ForgeTheme {
            WorkshopRoot()
        }
    }
}

/** P4 step 1: themed shell. Foundry, Skill Runner, Widget and tray land on top of this. */
@Composable
private fun WorkshopRoot() {
    Surface(color = forgeColors.ground, modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(forgeColors.emberHot, forgeColors.ember))),
                )
                Spacer(Modifier.height(18.dp))
                Text("The Forge", color = forgeColors.ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Workshop", color = forgeColors.inkMuted, fontSize = 15.sp)
            }
        }
    }
}
