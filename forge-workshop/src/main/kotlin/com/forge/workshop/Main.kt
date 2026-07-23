package com.forge.workshop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.forge.executors.secret.FileSecretStore
import com.forge.executors.secret.ForgeDirs
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.dashboard.DashboardHolder
import com.forge.workshop.dashboard.LiveDashboardRepository
import com.forge.workshop.theme.ForgeTheme
import com.forge.workshop.tray.SparkPainter
import com.forge.workshop.widget.TrayPopover
import com.forge.workshop.widget.WidgetPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main() = application {
    // Persistent secret store — tokens survive restarts (OS-keychain-backed store is the next upgrade).
    val secrets = remember { FileSecretStore(ForgeDirs.dataDir().resolve("secrets.properties")) }
    val appData = remember { AppDataStore(ForgeDirs.dataDir().resolve("appdata.json")) }
    val dashboard = remember { DashboardHolder(LiveDashboardRepository(secrets)) }
    val scope = rememberCoroutineScope()

    var refreshMinutes by remember { mutableStateOf(3) }
    var widgetVisible by remember { mutableStateOf(true) }
    var popoverVisible by remember { mutableStateOf(false) }

    // Poll on the chosen interval — but only while a consumer window is visible.
    LaunchedEffect(refreshMinutes, widgetVisible, popoverVisible) {
        if (widgetVisible || popoverVisible) {
            while (true) {
                dashboard.refresh()
                delay(refreshMinutes * 60_000L)
            }
        }
    }

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
            WorkshopApp(
                secrets = secrets,
                refreshMinutes = refreshMinutes,
                onIntervalChange = { refreshMinutes = it },
                onSaved = { scope.launch { dashboard.refresh() } },
                dashboardState = dashboard.state,
                onRefresh = { scope.launch { dashboard.refresh() } },
                store = appData,
            )
        }
    }

    // Standalone Widget window (compact, transparent, hover-expands, draggable)
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
            val awtWindow = window
            ForgeTheme {
                WidgetPanel(
                    state = dashboard.state,
                    onRefresh = { scope.launch { dashboard.refresh() } },
                    onMoveBy = { dx, dy -> awtWindow.setLocation(awtWindow.x + dx, awtWindow.y + dy) },
                )
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
            ForgeTheme {
                TrayPopover(
                    state = dashboard.state,
                    onRun = { popoverVisible = false },
                    onRefresh = { scope.launch { dashboard.refresh() } },
                )
            }
        }
    }
}
