package com.forge.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.executors.secret.ForgeDirs
import com.forge.sdk.secret.SecretStore
import com.forge.workshop.bench.BenchScreen
import com.forge.workshop.create.CreateIssueScreen
import com.forge.workshop.create.CreateMrScreen
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.dashboard.DashboardState
import com.forge.workshop.foundry.FoundryScreen
import com.forge.workshop.foundry.SkillSpec
import com.forge.workshop.history.HistoryScreen
import com.forge.workshop.history.HistoryStore
import com.forge.workshop.integrations.SettingsScreen
import com.forge.workshop.nav.NavItem
import com.forge.workshop.nav.NavRail
import com.forge.workshop.recipe.RecipeBuilderScreen
import com.forge.workshop.recipe.RecipeListScreen
import com.forge.workshop.recipe.RecipeRunnerScreen
import com.forge.workshop.recipe.SavedRecipe
import com.forge.workshop.runner.RunnerScreen
import com.forge.workshop.skills.Skill
import com.forge.workshop.skills.SkillEditorScreen
import com.forge.workshop.skills.SkillStore
import com.forge.workshop.skills.SkillsScreen
import com.forge.workshop.sparks.SparksScreen
import com.forge.workshop.theme.forgeColors
import com.forge.workshop.updater.AppVersion
import com.forge.workshop.updater.UpdateInfo
import com.forge.workshop.updater.Updater
import kotlinx.coroutines.launch

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
    onQuit: () -> Unit = {},
) {
    val updateScope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    // Startup check: announce a newer release as a Spark (deduped) and light the gear dot.
    LaunchedEffect(Unit) {
        Updater.checkForUpdate()?.let { info ->
            update = info
            store.recordUpdate(info.version, info.notes)
            updateStatus = "доступна ${info.version}"
        }
    }
    fun checkUpdate() = updateScope.launch {
        updateStatus = "проверка…"
        val info = Updater.checkForUpdate()
        update = info
        updateStatus = if (info != null) {
            store.recordUpdate(info.version, info.notes); "доступна ${info.version}"
        } else "актуальная версия"
    }
    fun installUpdate() = updateScope.launch {
        val info = update ?: return@launch
        updateStatus = "скачивание…"
        try {
            val file = Updater.download(info)
            updateStatus = "запуск установщика…"
            Updater.launchInstaller(file)
            onQuit()
        } catch (e: Exception) {
            updateStatus = "ошибка: ${e.message}"
        }
    }
    var selected by remember { mutableStateOf(NavItem.BENCH) }
    var running by remember { mutableStateOf<SkillSpec?>(null) }
    var builderOpen by remember { mutableStateOf(false) }
    var builderInitial by remember { mutableStateOf<SavedRecipe?>(null) }
    var runnerRecipe by remember { mutableStateOf<SavedRecipe?>(null) }
    val skillStore = remember { SkillStore(ForgeDirs.dataDir().resolve("skills")) }
    var skillEditorOpen by remember { mutableStateOf(false) }
    var skillEditorInitial by remember { mutableStateOf<Skill?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableStateOf(0) }
    val history = remember { HistoryStore() }

    // Fetch fresh data whenever the Bench is opened (background polling only runs while the
    // widget/popover is visible). Opening Sparks clears the unread markers.
    LaunchedEffect(selected) {
        if (selected == NavItem.BENCH) onRefresh()
        if (selected == NavItem.SPARKS) store.markNotificationsRead()
    }

    Surface(color = forgeColors.ground, modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavRail(
                selected,
                onSelect = { selected = it; running = null; builderOpen = false; runnerRecipe = null; skillEditorOpen = false; settingsOpen = false },
                unread = store.unreadCount(),
                settingsActive = settingsOpen,
                updateAvailable = update != null,
                onSettings = { settingsOpen = true; settingsTab = 0 },
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(forgeColors.border))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val current = running
                when {
                    settingsOpen -> SettingsScreen(
                        secrets = secrets,
                        refreshMinutes = refreshMinutes,
                        onIntervalChange = onIntervalChange,
                        onSaved = onSaved,
                        store = store,
                        currentVersion = AppVersion.CURRENT,
                        update = update,
                        updateStatus = updateStatus,
                        onCheckUpdate = { checkUpdate() },
                        onInstallUpdate = { installUpdate() },
                        initialTab = settingsTab,
                    )
                    current != null && current.title == "Create Jira Story" -> CreateIssueScreen(
                        secrets = secrets,
                        store = store,
                        onBack = { running = null },
                        onFinished = { ok -> history.record("Create Jira Story", ok) },
                    )
                    current != null && current.title == "Open GitLab MR" -> CreateMrScreen(
                        secrets = secrets,
                        onBack = { running = null },
                        onFinished = { ok -> history.record("Open GitLab MR", ok) },
                    )
                    current != null -> RunnerScreen(
                        skill = current,
                        onBack = { running = null },
                        onFinished = { ok -> history.record(current.title, ok) },
                    )
                    selected == NavItem.RECIPES && runnerRecipe != null -> RecipeRunnerScreen(
                        recipe = runnerRecipe!!,
                        appData = store,
                        secrets = secrets,
                        skillStore = skillStore,
                        onFinished = { ok -> history.record("Рецепт: ${runnerRecipe?.name ?: ""}", ok) },
                        onBack = { runnerRecipe = null },
                    )
                    selected == NavItem.RECIPES && builderOpen -> RecipeBuilderScreen(
                        store = store,
                        initial = builderInitial,
                        onBack = { builderOpen = false },
                    )
                    selected == NavItem.RECIPES -> RecipeListScreen(
                        store = store,
                        onNew = { builderInitial = null; builderOpen = true },
                        onEdit = { builderInitial = it; builderOpen = true },
                        onRun = { runnerRecipe = it },
                    )
                    selected == NavItem.BENCH -> BenchScreen(dashboardState, store, onRefresh, onRunRecipe = { runnerRecipe = it; selected = NavItem.RECIPES })
                    selected == NavItem.FOUNDRY -> FoundryScreen(onRun = { running = it })
                    selected == NavItem.SKILLS && skillEditorOpen -> SkillEditorScreen(
                        store = skillStore,
                        project = store.data.skillProject,
                        initial = skillEditorInitial,
                        onBack = { skillEditorOpen = false },
                    )
                    selected == NavItem.SKILLS -> SkillsScreen(
                        appData = store,
                        skillStore = skillStore,
                        onNew = { skillEditorInitial = null; skillEditorOpen = true },
                        onEdit = { skillEditorInitial = it; skillEditorOpen = true },
                    )
                    selected == NavItem.SPARKS -> SparksScreen(store, onOpenUpdate = { settingsOpen = true; settingsTab = 3 })
                    selected == NavItem.HISTORY -> HistoryScreen(history)
                    else -> {}
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
