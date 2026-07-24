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
import com.forge.workshop.data.NType
import com.forge.workshop.data.NotificationEvent
import com.forge.workshop.dashboard.DashboardData
import com.forge.workshop.dashboard.DashboardHolder
import com.forge.workshop.dashboard.DashboardState
import com.forge.workshop.dashboard.LiveDashboardRepository
import java.util.UUID
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

    // Refresh the dashboard, then diff the Jira issues against the last snapshot to emit Sparks.
    suspend fun refreshAndDetect() {
        dashboard.refresh()
        (dashboard.state as? DashboardState.Loaded)?.let { detectSparks(it.data, appData) }
    }

    // Poll on the chosen interval — but only while a consumer window is visible.
    LaunchedEffect(refreshMinutes, widgetVisible, popoverVisible) {
        if (widgetVisible || popoverVisible) {
            while (true) {
                refreshAndDetect()
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
                onSaved = { scope.launch { refreshAndDetect() } },
                dashboardState = dashboard.state,
                onRefresh = { scope.launch { refreshAndDetect() } },
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
                    onRefresh = { scope.launch { refreshAndDetect() } },
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
                    onRefresh = { scope.launch { refreshAndDetect() } },
                )
            }
        }
    }
}

/**
 * Diff the freshly-loaded Jira issues against the last snapshot and persist any Sparks.
 * NEW — a key we hadn't seen (only after the first non-empty snapshot, to avoid flooding on cold start).
 * STATUS — the issue's status name changed since last poll.
 */
private fun detectSparks(data: DashboardData, store: AppDataStore) {
    val prev = store.data.issueSnapshot
    val snapshot = data.jira.associate { it.code to (it.statusName ?: "") }
    val now = System.currentTimeMillis()
    val events = buildList {
        for (row in data.jira) {
            val key = row.code
            val status = row.statusName ?: ""
            val old = prev[key]
            when {
                old == null -> if (prev.isNotEmpty()) add(
                    NotificationEvent(UUID.randomUUID().toString(), NType.NEW, key, row.text, "Новая · $status", now),
                )
                old != status -> add(
                    NotificationEvent(UUID.randomUUID().toString(), NType.STATUS, key, row.text, "$old → $status", now),
                )
            }
        }
    }
    if (events.isNotEmpty() || snapshot != prev) store.recordNotifications(events, snapshot)
}
