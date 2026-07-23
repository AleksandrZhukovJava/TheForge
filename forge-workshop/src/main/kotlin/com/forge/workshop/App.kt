package com.forge.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.sdk.secret.SecretStore
import com.forge.workshop.bench.BenchScreen
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.dashboard.DashboardState
import com.forge.workshop.foundry.FoundryScreen
import com.forge.workshop.foundry.SkillSpec
import com.forge.workshop.history.HistoryScreen
import com.forge.workshop.history.HistoryStore
import com.forge.workshop.integrations.IntegrationsScreen
import com.forge.workshop.nav.NavItem
import com.forge.workshop.nav.NavRail
import com.forge.workshop.runner.RunnerScreen
import com.forge.workshop.theme.forgeColors

/** Root of the main Workshop window: nav rail + the selected screen (or a running Skill). */
@Composable
fun WorkshopApp(
    secrets: SecretStore,
    refreshMinutes: Int,
    onIntervalChange: (Int) -> Unit,
    onSaved: () -> Unit,
    dashboardState: DashboardState,
    onRefresh: () -> Unit,
    store: AppDataStore,
) {
    var selected by remember { mutableStateOf(NavItem.BENCH) }
    var running by remember { mutableStateOf<SkillSpec?>(null) }
    val history = remember { HistoryStore() }

    // Fetch fresh data whenever the Bench is opened (background polling only runs while the
    // widget/popover is visible).
    LaunchedEffect(selected) {
        if (selected == NavItem.BENCH) onRefresh()
    }

    Surface(color = forgeColors.ground, modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavRail(selected) { selected = it; running = null }
            Box(Modifier.width(1.dp).fillMaxHeight().background(forgeColors.border))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val current = running
                when {
                    current != null -> RunnerScreen(
                        skill = current,
                        onBack = { running = null },
                        onFinished = { ok -> history.record(current.title, ok) },
                    )
                    selected == NavItem.BENCH -> BenchScreen(dashboardState, store, onRefresh)
                    selected == NavItem.FOUNDRY -> FoundryScreen(onRun = { running = it })
                    selected == NavItem.HISTORY -> HistoryScreen(history)
                    else -> IntegrationsScreen(secrets, refreshMinutes, onIntervalChange, onSaved, store)
                }
            }
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = forgeColors.inkFaint, fontSize = 15.sp)
    }
}
