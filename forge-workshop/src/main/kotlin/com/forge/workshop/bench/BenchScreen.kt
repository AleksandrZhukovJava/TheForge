package com.forge.workshop.bench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.data.Priority
import com.forge.workshop.dashboard.DashboardState
import com.forge.workshop.theme.forgeColors
import com.forge.workshop.ui.PillStatus
import com.forge.workshop.ui.StatusPill
import com.forge.workshop.widget.WRow

private data class BenchTask(
    val id: String,
    val code: String,
    val title: String,
    val status: PillStatus?,
    val priority: Priority,
    val url: String?,
    val isLocal: Boolean,
)

/** Bench — the workbench: your Jira tasks + your own local tasks (ranked by priority) and MRs. */
@Composable
fun BenchScreen(state: DashboardState, store: AppDataStore, onRefresh: () -> Unit) {
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

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TasksColumn(state, store, Modifier.weight(1f))
            MrColumn(state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TasksColumn(state: DashboardState, store: AppDataStore, modifier: Modifier) {
    val jira = (state as? DashboardState.Loaded)?.data?.jira.orEmpty()
    val tasks = buildList {
        jira.forEach { add(BenchTask(it.code, it.code, it.text, it.status, store.jiraPriority(it.code), it.url, false)) }
        store.data.localTasks.forEach { add(BenchTask(it.id, "своя", it.summary, null, it.priority, null, true)) }
    }.sortedByDescending { it.priority.ordinal }

    Column(modifier.fillMaxHeight()) {
        SectionHeader("Мои задачи", forgeColors.tool, tasks.size)
        Spacer(Modifier.height(10.dp))
        AddLocalTask { store.addLocalTask(it) }
        Spacer(Modifier.height(10.dp))
        if (state is DashboardState.Error) {
            Text("Jira: ${state.message}", color = forgeColors.crit, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        } else if (state is DashboardState.NotConfigured) {
            Text("Jira не подключена — свои задачи всё равно работают.", color = forgeColors.inkFaint, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tasks.forEach { task ->
                TaskCard(
                    task = task,
                    onCyclePriority = {
                        if (task.isLocal) store.cycleLocalPriority(task.id) else store.cycleJiraPriority(task.code)
                    },
                    onDelete = if (task.isLocal) ({ store.deleteLocalTask(task.id) }) else null,
                )
            }
        }
    }
}

@Composable
private fun MrColumn(state: DashboardState, modifier: Modifier) {
    Column(modifier.fillMaxHeight()) {
        val mrs = (state as? DashboardState.Loaded)?.data?.mrs.orEmpty()
        SectionHeader("Merge Requests", forgeColors.press, mrs.size)
        Spacer(Modifier.height(10.dp))
        when (state) {
            is DashboardState.Loaded ->
                if (mrs.isEmpty()) Hint("пусто")
                else Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { mrs.forEach { MrCard(it) } }
            DashboardState.Loading -> Hint("загрузка…")
            is DashboardState.Error -> Hint("ошибка: ${state.message}", forgeColors.crit)
            DashboardState.NotConfigured -> Hint("подключите GitLab в Integrations")
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
    fun submit() {
        onAdd(text)
        text = ""
    }
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
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(forgeColors.ember)
                .clickable { submit() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text("＋", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TaskCard(task: BenchTask, onCyclePriority: () -> Unit, onDelete: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(11.dp))
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PriorityChip(task.priority, onCyclePriority)
            Spacer(Modifier.width(9.dp))
            Text(task.code, color = if (task.isLocal) forgeColors.ember else forgeColors.inkMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            if (task.status != null) StatusPill(task.status)
            if (onDelete != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "✕",
                    color = forgeColors.inkFaint,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onDelete() }.padding(2.dp),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(task.title, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (task.url != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "открыть ↗",
                color = forgeColors.ember,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { openInBrowser(task.url) },
            )
        }
    }
}

@Composable
private fun MrCard(row: WRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(11.dp))
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.code, color = forgeColors.inkMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            StatusPill(row.status)
        }
        Spacer(Modifier.height(7.dp))
        Text(row.text, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (row.url != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "открыть ↗",
                color = forgeColors.ember,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { openInBrowser(row.url) },
            )
        }
    }
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
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Hint(text: String, color: Color = forgeColors.inkMuted) {
    Text(text, color = color, fontSize = 13.sp)
}

private fun openInBrowser(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {
        // browser unavailable — ignore
    }
}
