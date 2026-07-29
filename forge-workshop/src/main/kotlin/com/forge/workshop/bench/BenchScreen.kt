package com.forge.workshop.bench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.data.LOCAL_STATUS
import com.forge.workshop.data.Priority
import com.forge.workshop.dashboard.DashboardState
import com.forge.workshop.recipe.SavedRecipe
import com.forge.workshop.theme.forgeColors
import com.forge.workshop.ui.PillStatus
import com.forge.workshop.ui.StatusPill

private data class BenchTask(
    val id: String,
    val code: String,
    val title: String,
    val status: PillStatus?,
    val priority: Priority,
    val url: String?,
    val isLocal: Boolean,
    val current: Boolean,
    val blocked: Boolean,
    val statusName: String?,
)

private enum class BenchTab(val label: String) {
    ALL("Все"), PRIORITY("Приоритет"), BLOCKED("Заблок."), LOCAL("Свои"), DONE("Готово"), ARCHIVE("Архив")
}

/** Bench — the workbench: Jira tasks + your own tasks, with priority, overlays and MRs. */
@Composable
fun BenchScreen(state: DashboardState, store: AppDataStore, onRefresh: () -> Unit, onRunRecipe: (SavedRecipe) -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bench", color = forgeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(12.dp))
            Text("верстак — над чем вы работаете сейчас", color = forgeColors.inkFaint, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "⟳ обновить",
                color = forgeColors.inkMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .border(1.dp, forgeColors.borderStrong, RoundedCornerShape(8.dp))
                    .clickable { onRefresh() }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        TasksColumn(state, store, onRunRecipe, Modifier.fillMaxSize())
    }
}

@Composable
private fun TasksColumn(state: DashboardState, store: AppDataStore, onRunRecipe: (SavedRecipe) -> Unit, modifier: Modifier) {
    val jira = (state as? DashboardState.Loaded)?.data?.jira.orEmpty()
    val all = buildList {
        jira.forEach {
            add(BenchTask(it.code, it.code, it.text, it.status, store.jiraPriority(it.code), it.url, false, it.code in store.data.current, it.code in store.data.blocked, it.statusName))
        }
        store.data.localTasks.forEach {
            add(BenchTask(it.id, "своя", it.summary, null, it.priority, null, true, it.id in store.data.current, it.id in store.data.blocked, LOCAL_STATUS))
        }
    }
    val doneIds = store.data.done
    val archivedIds = store.data.archived
    val active = all.filterNot { it.id in doneIds || it.id in archivedIds }
    val done = all.filter { it.id in doneIds && it.id !in archivedIds }
    val archived = all.filter { it.id in archivedIds }

    val cmp = compareByDescending<BenchTask> { it.current }.thenBy { it.blocked }.thenByDescending { it.priority.ordinal }
    val blocks = store.data.blocks
    fun blockName(t: BenchTask): String =
        blocks.firstOrNull { b -> b.statuses.any { it.equals(t.statusName, ignoreCase = true) } }?.name ?: "Прочее"
    val grouped = active.groupBy { blockName(it) }
    val orderedNames = (blocks.map { it.name } + "Прочее").distinct()

    val priority = active.filter { it.priority != Priority.NONE }.sortedByDescending { it.priority.ordinal }
    val blocked = active.filter { it.blocked }.sortedWith(cmp)
    val local = active.filter { it.isLocal }.sortedWith(cmp)

    var tab by remember { mutableStateOf(BenchTab.ALL) }
    var editingId by remember { mutableStateOf<String?>(null) }

    val counts = mapOf(
        BenchTab.ALL to active.size, BenchTab.PRIORITY to priority.size, BenchTab.BLOCKED to blocked.size,
        BenchTab.LOCAL to local.size, BenchTab.DONE to done.size, BenchTab.ARCHIVE to archived.size,
    )

    Column(modifier.fillMaxHeight()) {
        SectionHeader("Мои задачи", forgeColors.tool, active.size)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BenchTab.entries.forEach { t -> BenchTabChip(t.label, counts[t] ?: 0, t == tab) { tab = t } }
        }
        Spacer(Modifier.height(10.dp))
        AddLocalTask { store.addLocalTask(it) }
        Spacer(Modifier.height(10.dp))
        if (state is DashboardState.Error) {
            Text("Jira: ${state.message}", color = forgeColors.crit, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (tab == BenchTab.ALL) 16.dp else 10.dp),
        ) {
            when (tab) {
                BenchTab.ALL -> orderedNames.forEach { name ->
                    val items = grouped[name].orEmpty().sortedWith(cmp)
                    if (items.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            BlockHeader(name, items.size)
                            items.forEach { task -> BenchCard(task, store, editingId, { editingId = it }, onRunRecipe) }
                        }
                    }
                }
                BenchTab.PRIORITY -> TaskList(priority, store, editingId, { editingId = it }, onRunRecipe)
                BenchTab.BLOCKED -> TaskList(blocked, store, editingId, { editingId = it }, onRunRecipe)
                BenchTab.LOCAL -> TaskList(local, store, editingId, { editingId = it }, onRunRecipe)
                BenchTab.DONE -> TaskList(done, store, editingId, { editingId = it }, onRunRecipe)
                BenchTab.ARCHIVE -> TaskList(archived, store, editingId, { editingId = it }, onRunRecipe)
            }
        }
    }
}

/** A flat list of task cards with an empty-state hint. */
@Composable
private fun TaskList(items: List<BenchTask>, store: AppDataStore, editingId: String?, onEditing: (String?) -> Unit, onRunRecipe: (SavedRecipe) -> Unit) {
    if (items.isEmpty()) {
        Text("пусто", color = forgeColors.inkMuted, fontSize = 13.sp)
    } else {
        items.forEach { task -> BenchCard(task, store, editingId, onEditing, onRunRecipe) }
    }
}

@Composable
private fun BenchCard(task: BenchTask, store: AppDataStore, editingId: String?, onEditing: (String?) -> Unit, onRunRecipe: (SavedRecipe) -> Unit) {
    TaskCard(
        task = task,
        store = store,
        isEditing = editingId == task.id,
        onToggleEdit = { onEditing(if (editingId == task.id) null else task.id) },
        onSaveEdit = { store.updateLocalTask(task.id, it); onEditing(null) },
        onRunRecipe = onRunRecipe,
    )
}

@Composable
private fun BenchTabChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(forgeColors.ember.copy(alpha = 0.15f)).border(1.dp, forgeColors.ember, RoundedCornerShape(8.dp)) else Modifier.border(1.dp, forgeColors.border, RoundedCornerShape(8.dp)))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            "$label${if (count > 0) " · $count" else ""}",
            color = if (selected) forgeColors.ember else forgeColors.inkMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun BlockHeader(name: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name.uppercase(), color = forgeColors.inkMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = forgeColors.inkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(forgeColors.border))
    }
}

@Composable
private fun TaskCard(
    task: BenchTask,
    store: AppDataStore,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onSaveEdit: (String) -> Unit,
    onRunRecipe: (SavedRecipe) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(11.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(11.dp)),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (task.current) forgeColors.good else Color.Transparent))
        Column(Modifier.weight(1f).padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriorityChip(task.priority) {
                    if (task.isLocal) store.cycleLocalPriority(task.id) else store.cycleJiraPriority(task.code)
                }
                Spacer(Modifier.width(9.dp))
                Text(task.code, color = if (task.isLocal) forgeColors.ember else forgeColors.inkMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                if (task.blocked) {
                    Text("заблок.", color = forgeColors.crit, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                }
                if (task.status != null) StatusPill(task.status, task.statusName ?: task.status.label)
            }
            Spacer(Modifier.height(7.dp))
            if (isEditing && task.isLocal) {
                EditField(task.title, onSaveEdit)
            } else {
                Text(task.title, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Action("★ текущая", task.current, forgeColors.good) { store.toggleCurrent(task.id) }
                Action("заблок.", task.blocked, forgeColors.crit) { store.toggleBlocked(task.id) }
                Action("✓ готово", false, forgeColors.good) { store.toggleDone(task.id) }
                Action("🗄 архив", false, forgeColors.inkMuted) { store.toggleArchived(task.id) }
                if (task.isLocal) {
                    Action("✎", isEditing, forgeColors.tool) { onToggleEdit() }
                    Action("✕", false, forgeColors.crit) { store.deleteLocalTask(task.id) }
                }
                RecipeControl(task.id, store, onRunRecipe)
                if (task.url != null) {
                    Spacer(Modifier.weight(1f))
                    Text("открыть ↗", color = forgeColors.ember, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { openInBrowser(task.url) })
                }
            }
        }
    }
}

/** Bind / run a saved recipe for a task. Bound recipe name shows in Smith color; menu picks/unbinds. */
@Composable
private fun RecipeControl(taskId: String, store: AppDataStore, onRun: (SavedRecipe) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val bound = store.taskRecipe(taskId)
    Box {
        Action(if (bound != null) "▶ ${bound.name}" else "рецепт ▾", bound != null, forgeColors.smith) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val recipes = store.data.recipes
            if (recipes.isEmpty()) {
                DropdownMenuItem(text = { Text("нет рецептов — создайте в Recipes", fontSize = 12.sp) }, onClick = { open = false })
            } else {
                recipes.forEach { r ->
                    DropdownMenuItem(text = { Text(r.name) }, onClick = { store.setTaskRecipe(taskId, r.id); open = false; onRun(r) })
                }
                if (bound != null) DropdownMenuItem(text = { Text("отвязать", color = forgeColors.crit) }, onClick = { store.setTaskRecipe(taskId, null); open = false })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, accent: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(9.dp))
        Text(title, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = forgeColors.inkFaint, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun AddLocalTask(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() { onAdd(text); text = "" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("своя задача…", fontSize = 13.sp) },
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(forgeColors.ember).clickable { submit() }.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text("＋", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditField(initial: String, onDone: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        keyboardActions = KeyboardActions(onDone = { onDone(value) }),
        trailingIcon = {
            Text("↵", color = forgeColors.ember, fontSize = 15.sp, modifier = Modifier.clickable { onDone(value) }.padding(end = 8.dp))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Action(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) activeColor else forgeColors.inkFaint,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onClick() }.padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun PriorityChip(priority: Priority, onClick: () -> Unit) {
    val (label, color) = when (priority) {
        Priority.HIGH -> "выс" to forgeColors.crit
        Priority.MEDIUM -> "сред" to forgeColors.warn
        Priority.LOW -> "низ" to forgeColors.tool
        Priority.NONE -> "—" to forgeColors.inkFaint
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

private fun openInBrowser(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {
        // browser unavailable — ignore
    }
}
