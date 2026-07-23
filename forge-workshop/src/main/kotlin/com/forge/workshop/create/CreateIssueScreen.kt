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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.forge.integration.jira.CreateJiraIssueTool
import com.forge.integration.jira.JiraAuth
import com.forge.integration.jira.JiraClient
import com.forge.integration.jira.JiraConfig
import com.forge.integration.jira.JiraIssueRef
import com.forge.integration.jira.JiraIssueType
import com.forge.integration.jira.JiraProject
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.secret.SecretStore
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.runner.ConfirmModal
import com.forge.workshop.runner.UiMasterGate
import com.forge.workshop.theme.forgeColors
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.launch

private sealed interface CreateStatus {
    data object Idle : CreateStatus
    data object Running : CreateStatus
    data class Success(val key: String, val url: String) : CreateStatus
    data object Rejected : CreateStatus
    data class Failed(val message: String) : CreateStatus
}

/**
 * Runs [block] with a working JiraClient. Probes auth once (Basic if email is set, then Bearer PAT)
 * via `/myself`, so the caller's block runs exactly once with the auth that Jira actually accepts —
 * no double Master prompt, and no reliance on which scheme the dashboard happened to use.
 */
private suspend fun <T> withJira(secrets: SecretStore, block: suspend (JiraClient, base: String) -> T): T {
    val base = secrets.get("jira.base-url")?.trimEnd('/') ?: error("Jira не настроена — заполните Integrations")
    val token = secrets.get("jira.token") ?: error("Jira не настроена — заполните Integrations")
    val email = secrets.get("jira.email")
    val auths = buildList {
        if (!email.isNullOrBlank()) add(JiraAuth.basic(email, token))
        add(JiraAuth.bearer(token))
    }
    val http = HttpClient(CIO)
    return try {
        var working: JiraClient? = null
        var last: Exception? = null
        for (auth in auths) {
            val client = JiraClient(http, JiraConfig(base), auth)
            try {
                client.ping()
                working = client
                break
            } catch (e: Exception) {
                last = e
            }
        }
        val client = working ?: throw (last ?: IllegalStateException("Jira недоступна"))
        block(client, base)
    } finally {
        http.close()
    }
}

/** "Create Jira Story" — a real Skill from Foundry: reads projects & types, then creates via the engine. */
@Composable
fun CreateIssueScreen(
    secrets: SecretStore,
    store: AppDataStore,
    onBack: () -> Unit,
    onFinished: (Boolean) -> Unit,
) {
    var projects by remember { mutableStateOf<List<JiraProject>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var project by remember { mutableStateOf(store.data.jiraProjectKey) }
    var types by remember { mutableStateOf<List<JiraIssueType>?>(null) }
    var issueType by remember { mutableStateOf("Task") }
    var summary by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<CreateStatus>(CreateStatus.Idle) }
    val gate = remember { UiMasterGate() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val ps = withJira(secrets) { c, _ -> c.getProjects() }
            projects = ps
            if (project.isBlank()) project = ps.firstOrNull()?.key.orEmpty()
        } catch (e: Exception) {
            loadError = e.message
        }
    }
    LaunchedEffect(project, projects) {
        if (project.isNotBlank() && projects != null) {
            types = try {
                val ts = withJira(secrets) { c, _ -> c.getIssueTypes(project) }
                if (ts.none { it.name == issueType }) issueType = ts.firstOrNull()?.name ?: issueType
                ts
            } catch (e: Exception) {
                null
            }
        }
    }

    fun submit() {
        if (project.isBlank() || summary.isBlank()) {
            status = CreateStatus.Failed("укажите проект и заголовок")
            return
        }
        store.setJiraProjectKey(project)
        scope.launch {
            status = CreateStatus.Running
            try {
                val outcome = withJira(secrets) { client, base ->
                    val host = runCatching { java.net.URI(base).host }.getOrNull() ?: base
                    val registry = DefaultCapabilityRegistry().apply { register(CreateJiraIssueTool(client, host)) }
                    val executor = StrikeExecutor(StrikeResolver(registry), gate, PolicyEngine(DefaultPolicy))
                    val strike = StrikeDecl(
                        StrikeId("create"),
                        CapabilityId("jira.create-issue"),
                        DangerLevel.CONFIRM,
                        input = mapOf("project" to project, "summary" to summary, "issueType" to issueType),
                    )
                    executor.run(strike, Stock.EMPTY) to base
                }
                status = when (val o = outcome.first) {
                    is StrikeOutcome.Done -> {
                        val key = (o.result.output as? JiraIssueRef)?.key ?: "?"
                        onFinished(true)
                        CreateStatus.Success(key, "${outcome.second}/browse/$key")
                    }
                    is StrikeOutcome.Rejected -> { onFinished(false); CreateStatus.Rejected }
                    is StrikeOutcome.Blocked -> { onFinished(false); CreateStatus.Failed(o.reason) }
                }
            } catch (e: Exception) {
                onFinished(false)
                status = CreateStatus.Failed(e.message ?: "ошибка создания")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChip(onBack)
                Spacer(Modifier.width(14.dp))
                Text("Create Jira Story", color = forgeColors.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(18.dp))
            Column(modifier = Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    projects != null -> SearchPicker("Проект", projects!!.map { it.key to "${it.key} — ${it.name}" }, project) { project = it }
                    loadError != null -> {
                        OutlinedTextField(project, { project = it }, label = { Text("Проект (key)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("не удалось прочитать проекты (${loadError}) — введите вручную", color = forgeColors.warn, fontSize = 11.sp)
                    }
                    else -> LoadingField("Проект", "читаю проекты…")
                }

                when {
                    types != null && types!!.isNotEmpty() -> SearchPicker("Тип задачи", types!!.map { it.name to it.name }, issueType) { issueType = it }
                    else -> OutlinedTextField(issueType, { issueType = it }, label = { Text("Тип задачи") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }

                OutlinedTextField(summary, { summary = it }, label = { Text("Заголовок") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(if (status == CreateStatus.Running) "Создаётся…" else "Создать", enabled = status != CreateStatus.Running) { submit() }
                    Spacer(Modifier.width(14.dp))
                    StatusLine(status)
                }
                Text("AI-формулировка по описанию — следующий шаг (агент/LLM).", color = forgeColors.inkFaint, fontSize = 11.sp)
            }
        }
        gate.pending?.let { request ->
            ConfirmModal(request, onApprove = { gate.answer(true) }, onReject = { gate.answer(false) })
        }
    }
}

/** Type-to-filter picker: keep focus in the field (non-focusable menu) so you can search live. */
@Composable
private fun SearchPicker(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
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
private fun LoadingField(label: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, forgeColors.border, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(label, color = forgeColors.inkFaint, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(text, color = forgeColors.inkMuted, fontSize = 14.sp)
    }
}

@Composable
private fun StatusLine(status: CreateStatus) {
    when (status) {
        CreateStatus.Idle, CreateStatus.Running -> {}
        is CreateStatus.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Создана ${status.key}", color = forgeColors.good, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text("открыть ↗", color = forgeColors.ember, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { openInBrowser(status.url) })
        }
        CreateStatus.Rejected -> Text("отклонено", color = forgeColors.crit, fontSize = 13.sp)
        is CreateStatus.Failed -> Text(status.message, color = forgeColors.crit, fontSize = 13.sp)
    }
}

@Composable
private fun BackChip(onBack: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, forgeColors.borderStrong, RoundedCornerShape(8.dp)).clickable { onBack() }.padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text("← Foundry", color = forgeColors.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (enabled) forgeColors.ember else forgeColors.borderStrong)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun openInBrowser(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {
        // ignore
    }
}
