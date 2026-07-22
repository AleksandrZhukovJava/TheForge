package com.forge.workshop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.forge.workshop.theme.ForgeTheme
import com.forge.workshop.tray.SparkPainter
import com.forge.workshop.widget.TrayPopover
import com.forge.workshop.widget.WidgetPanel

fun main() = application {
    var widgetVisible by remember { mutableStateOf(true) }
    var popoverVisible by remember { mutableStateOf(false) }

    Tray(
        icon = remember { SparkPainter() },
        tooltip = "The Forge",
        onAction = { popoverVisible = !popoverVisible },
        menu = {
            Item(if (widgetVisible) "Скрыть виджет" else "Показать виджет") { widgetVisible = !widgetVisible }
            Item(if (popoverVisible) "Скрыть панель" else "Быстрый доступ") { popoverVisible = !popoverVisible }
            Separator()
            Item("Выход") { exitApplication() }
        },
    )

    // Main Workshop window
    val mainState = rememberWindowState(size = DpSize(1060.dp, 700.dp))
    Window(onCloseRequest = ::exitApplication, state = mainState, title = "The Forge — Workshop") {
        ForgeTheme {
            WorkshopApp()
        }
    }

    // Standalone Widget window (compact, transparent, hover-expands)
    if (widgetVisible) {
        val widgetState = rememberWindowState(
            size = DpSize(300.dp, 440.dp),
            position = WindowPosition(Alignment.TopEnd),
        )
        Window(
            onCloseRequest = { widgetVisible = false },
            state = widgetState,
            title = "The Forge — Widget",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            // Undecorated window: drag the bar to move the AWT window.
            val awtWindow = window
            ForgeTheme {
                WidgetPanel(onDrag = { delta ->
                    awtWindow.setLocation(
                        awtWindow.x + delta.x.toInt(),
                        awtWindow.y + delta.y.toInt(),
                    )
                })
            }
        }
    }

    // Tray popover window
    if (popoverVisible) {
        val popoverState = rememberWindowState(
            size = DpSize(340.dp, 360.dp),
            position = WindowPosition(Alignment.BottomEnd),
        )
        Window(
            onCloseRequest = { popoverVisible = false },
            state = popoverState,
            title = "The Forge",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            ForgeTheme { TrayPopover(onRun = { popoverVisible = false }) }
        }
    }
}
