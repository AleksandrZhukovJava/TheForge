package com.forge.workshop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.forge.workshop.theme.ForgeTheme

fun main() = application {
    val state = rememberWindowState(size = DpSize(1060.dp, 700.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "The Forge — Workshop",
    ) {
        ForgeTheme {
            WorkshopApp()
        }
    }
}
