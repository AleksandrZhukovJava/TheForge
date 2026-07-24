package com.forge.workshop.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.PopupProperties
import com.forge.brain.execute.StrikeExecutor
import com.forge.brain.execute.StrikeOutcome
import com.forge.brain.policy.DefaultPolicy
import com.forge.brain.policy.PolicyEngine
import com.forge.brain.resolve.DefaultCapabilityRegistry
import com.forge.brain.resolve.StrikeResolver
import com.forge.integration.gitlab.GitLabBranch
import com.forge.integration.gitlab.GitLabClient
import com.forge.integration.gitlab.GitLabConfig
import com.forge.integration.gitlab.GitLabProject
import com.forge.integration.gitlab.MergeRequest
import com.forge.integration.gitlab.OpenMergeRequestTool
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.secret.SecretStore
import com.forge.workshop.runner.ConfirmModal
import com.forge.workshop.runner.UiMasterGate
import com.forge.workshop.theme.forgeColors
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.launch

private sealed interface MrStatus {
    data object Idle : MrStatus
    data object Running : MrStatus
    data class Success(val iid: Int, val url: String?) : MrStatus
    data object Rejected : MrStatus
    data class Failed(val message: String) : MrStatus
}

/**
 * Runs [block] with a working GitLab client. Probes the token once via `/user` so the caller's
 * side-effecting block runs exactly once with an accepted token — no double Master prompt.
 */
private suspend fun <T> withGitLab(secrets: SecretStore, block: suspend (GitLabClient, base: String) -> T): T {
    val base = secrets.get("gitlab.base-url")?.trimEnd('/') ?: error("GitLab не настроен — заполните Integrations")
    val token = secrets.get("gitlab.token") ?: error("GitLab не настроен — заполните Integrations")
    val http = HttpClient(CIO)
    return try {
        val client = GitLabClient(http, GitLabConfig(base), token)
        client.ping()
        block(client, base)
    } finally {
        http.close()
    }
}

/** "Open GitLab MR" — a real Skill from Foundry. Merge/force-push stay FORBIDDEN; this only opens. */
@Composable
fun CreateMrScreen(
    secrets: SecretStore,
    onBack: () -> Unit,
    onFinished: (Boolean) -> Unit,
) {
    var projects by remember { mutableStateOf<List<GitLabProject>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var project by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf<List<GitLabBranch>?>(null) }
    var source by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var removeSource by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<MrStatus>(MrStatus.Idle) }
    val gate = remember { UiMasterGate() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val ps = withGitLab(secrets) { c, _ -> c.getProjects() }
            projects = ps
            if (project.isBlank()) project = ps.firstOrNull()?.id?.toString().orEmpty()
        } catch (e: Exception) {
            loadError = e.message
        }
    }
    LaunchedEffect(project) {
        branches = null
        if (project.isNotBlank()) {
            branches = try {
                val bs = withGitLab(secrets) { c, _ -> c.getBranches(project) }
                target = bs.firstOrNull { it.default }?.name ?: bs.firstOrNull()?.name ?: target
                bs
            } catch (_: Exception) {
                null
            }
        }
    }

    fun submit() {
        if (project.isBlank() || source.isBlank() || target.isBlank() || title.isBlank()) {
            status = MrStatus.Failed("укажите проект, ветки и заголовок")
            return
        }
        if (source == target) {
            status = MrStatus.Failed("исходная и целевая ветки совпадают")
            return
        }
        scope.launch {
            status = MrStatus.Running
            try {
                val outcome = withGitLab(secrets) { client, base ->
                    val host = runCatching { java.net.URI(base).host }.getOrNull() ?: base
                    val registry = DefaultCapabilityRegistry().apply { register(OpenMergeRequestTool(client, host)) }
                    val executor = StrikeExecutor(StrikeResolver(registry), gate, PolicyEngine(DefaultPolicy))
                    val strike = StrikeDecl(
                        StrikeId("open-mr"),
                        CapabilityId("gitlab.open-merge-request"),
                        DangerLevel.CONFIRM,
                        input = mapOf(
                            "projectId" to project,
                            "sourceBranch" to source,
                            "targetBranch" to target,
                            "title" to title,
                            "description" to description,
                            "removeSourceBranch" to removeSource,
                        ),
                    )
                    executor.run(strike, Stock.EMPTY)
                }
                status = when (val o = outcome) {
                    is StrikeOutcome.Done -> {
                        val mr = o.result.output as? MergeRequest
                        onFinished(true)
                        MrStatus.Success(mr?.iid ?: 0, mr?.webUrl)
                    }
                    is StrikeOutcome.Rejected -> { onFinished(false); MrStatus.Rejected }
                    is StrikeOutcome.Blocked -> { onFinished(false); MrStatus.Failed(o.reason) }
                }
            } catch (e: Exception) {
                onFinished(false)
                status = MrStatus.Failed(e.message ?: "ошибка создания MR")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MrBackChip(onBack)
                Spacer(Modifier.width(14.dp))
                Text("Open GitLab MR", color = forgeColors.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(6.dp))
            Text("Merge и force-push запрещены — Skill только открывает merge request.", color = forgeColors.inkFaint, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Column(modifier = Modifier.width(560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    projects != null -> MrPicker("Проект", projects!!.map { it.id.toString() to (it.path.ifBlank { it.name }) }, project) { project = it }
                    loadError != null -> {
                        OutlinedTextField(project, { project = it }, label = { Text("Проект (id или path)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("не удалось прочитать проекты (${loadError}) — введите вручную", color = forgeColors.warn, fontSize = 11.sp)
                    }
                    else -> MrLoadingField("Проект", "читаю проекты…")
                }

                val branchOptions = branches?.map { it.name to (if (it.default) "${it.name} · default" else it.name) }
                when {
                    branchOptions != null && branchOptions.isNotEmpty() -> {
                        MrPicker("Исходная ветка", branchOptions, source) { source = it }
                        MrPicker("Целевая ветка", branchOptions, target) { target = it }
                    }
                    project.isNotBlank() && branches == null -> MrLoadingField("Ветки", "читаю ветки…")
                    else -> {
                        OutlinedTextField(source, { source = it }, label = { Text("Исходная ветка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(target, { target = it }, label = { Text("Целевая ветка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }

                OutlinedTextField(title, { title = it }, label = { Text("Заголовок") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Описание") }, minLines = 3, modifier = Modifier.fillMaxWidth())

                MrToggle("Удалить исходную ветку после merge", removeSource) { removeSource = !removeSource }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    MrPrimaryButton(if (status == MrStatus.Running) "Открывается…" else "Открыть MR", enabled = status != MrStatus.Running) { submit() }
                    Spacer(Modifier.width(14.dp))
                    MrStatusLine(status)
                }
            }
        }
        gate.pending?.let { request ->
            ConfirmModal(request, onApprove = { gate.answer(true) }, onReject = { gate.answer(false) })
        }
    }
}

/** Type-to-filter picker: keep focus in the field (non-focusable menu) so you can search live. */
@Composable
private fun MrPicker(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var query by remember(selected) { mutableStateOf(options.firstOrNull { it.first == selected }?.second ?: selected) }
    var expanded by remember { mutableStateOf(false) }
    val filtered = if (query.isBlank()) options else options.filter { it.second.contains(query, ignoreCase = true) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                Text("▾", color = forgeColors.inkMuted, fontSize = 14.sp, modifier = Modifier.clickable { expanded = !expanded }.padding(end = 8.dp))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
        ) {
            filtered.take(60).forEach { (key, lbl) ->
                DropdownMenuItem(text = { Text(lbl) }, onClick = { onSelect(key); query = lbl; expanded = false })
            }
        }
    }
}

@Composable
private fun MrLoadingField(label: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, forgeColors.border, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(label, color = forgeColors.inkFaint, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(text, color = forgeColors.inkMuted, fontSize = 14.sp)
    }
}

@Composable
private fun MrToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onToggle() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .then(if (checked) Modifier.background(forgeColors.ember) else Modifier.border(1.dp, forgeColors.borderStrong, RoundedCornerShape(5.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = forgeColors.inkMuted, fontSize = 13.sp)
    }
}

@Composable
private fun MrStatusLine(status: MrStatus) {
    when (status) {
        MrStatus.Idle, MrStatus.Running -> {}
        is MrStatus.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Открыт !${status.iid}", color = forgeColors.good, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (status.url != null) {
                Spacer(Modifier.width(10.dp))
                Text("открыть ↗", color = forgeColors.ember, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { openMrInBrowser(status.url) })
            }
        }
        MrStatus.Rejected -> Text("отклонено", color = forgeColors.crit, fontSize = 13.sp)
        is MrStatus.Failed -> Text(status.message, color = forgeColors.crit, fontSize = 13.sp)
    }
}

@Composable
private fun MrBackChip(onBack: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, forgeColors.borderStrong, RoundedCornerShape(8.dp)).clickable { onBack() }.padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text("← Foundry", color = forgeColors.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MrPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (enabled) forgeColors.ember else forgeColors.borderStrong)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun openMrInBrowser(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {
        // ignore
    }
}
