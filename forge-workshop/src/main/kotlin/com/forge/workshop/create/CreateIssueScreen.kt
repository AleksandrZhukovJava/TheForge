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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import com.forge.integration.jira.CreateField
import com.forge.integration.jira.CreateJiraIssueTool
import com.forge.integration.jira.FieldControl
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
import com.forge.workshop.llm.LlmClient
import com.forge.workshop.llm.LlmProfile
import com.forge.workshop.llm.LlmResult
import com.forge.workshop.runner.ConfirmModal
import com.forge.workshop.runner.UiMasterGate
import com.forge.workshop.theme.forgeColors
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    var description by remember { mutableStateOf("") }
    var aiText by remember { mutableStateOf("") }
    var refineText by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var genError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<CreateStatus>(CreateStatus.Idle) }
    val gate = remember { UiMasterGate() }
    val scope = rememberCoroutineScope()
    var fields by remember { mutableStateOf<List<CreateField>?>(null) }
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    val multiValues = remember { mutableStateMapOf<String, Set<String>>() }
    var extraOpen by remember { mutableStateOf(false) }
    val typeId = types?.firstOrNull { it.name == issueType }?.id

    fun buildExtra(): Map<String, JsonElement> {
        val out = mutableMapOf<String, JsonElement>()
        fields?.forEach { f ->
            when (f.control) {
                FieldControl.SELECT -> fieldValues[f.id]?.takeIf { it.isNotBlank() }?.let { out[f.id] = buildJsonObject { put("id", it) } }
                FieldControl.MULTISELECT -> multiValues[f.id]?.takeIf { it.isNotEmpty() }?.let { ids -> out[f.id] = buildJsonArray { ids.forEach { add(buildJsonObject { put("id", it) }) } } }
                FieldControl.LABELS -> fieldValues[f.id]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }?.let { labels -> out[f.id] = buildJsonArray { labels.forEach { add(it) } } }
                FieldControl.NUMBER -> fieldValues[f.id]?.toDoubleOrNull()?.let { out[f.id] = JsonPrimitive(it) }
                else -> fieldValues[f.id]?.takeIf { it.isNotBlank() }?.let { out[f.id] = JsonPrimitive(it) }
            }
        }
        return out
    }

    fun generate() {
        if (aiText.isBlank()) return
        scope.launch {
            generating = true
            genError = null
            val http = HttpClient(CIO)
            try {
                val profile = LlmProfile(store.data.llmBaseUrl.trimEnd('/'), store.data.llmModel, secrets.get("llm.apikey"))
                val result = LlmClient(http).generateTask(aiText, profile, store.data.llmPromptTemplate)
                summary = result.summary
                description = result.description
            } catch (e: Exception) {
                genError = e.message
            } finally {
                generating = false
                http.close()
            }
        }
    }

    fun refine() {
        if (refineText.isBlank() || (summary.isBlank() && description.isBlank())) return
        scope.launch {
            generating = true
            genError = null
            val http = HttpClient(CIO)
            try {
                val profile = LlmProfile(store.data.llmBaseUrl.trimEnd('/'), store.data.llmModel, secrets.get("llm.apikey"))
                val result = LlmClient(http).refineTask(refineText, LlmResult(summary, description), profile, store.data.llmPromptTemplate)
                summary = result.summary
                description = result.description
                refineText = ""
            } catch (e: Exception) {
                genError = e.message
            } finally {
                generating = false
                http.close()
            }
        }
    }

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
    LaunchedEffect(project, typeId) {
        fields = if (project.isNotBlank() && !typeId.isNullOrBlank()) {
            try {
                withJira(secrets) { c, _ -> c.getCreateFields(project, typeId) }
            } catch (e: Exception) {
                null
            }
        } else {
            null
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
                        input = mapOf("project" to project, "summary" to summary, "issueType" to issueType, "description" to description, "extraFields" to buildExtra()),
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
            Column(modifier = Modifier.width(560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                OutlinedTextField(
                    aiText,
                    { aiText = it },
                    label = { Text("Черновик — опишите задачу своими словами") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(if (generating) "Формулирую…" else "✨ Сформулировать", enabled = !generating && aiText.isNotBlank()) { generate() }
                    if (genError != null) {
                        Spacer(Modifier.width(12.dp))
                        Text(genError!!, color = forgeColors.crit, fontSize = 12.sp)
                    }
                }

                OutlinedTextField(summary, { summary = it }, label = { Text("Заголовок") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Описание") }, minLines = 3, modifier = Modifier.fillMaxWidth())

                if (summary.isNotBlank() || description.isNotBlank()) {
                    OutlinedTextField(
                        refineText,
                        { refineText = it },
                        label = { Text("Уточнение — что поправить (напр. «добавь критерии приёмки»)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        PrimaryButton(if (generating) "…" else "Уточнить", enabled = !generating && refineText.isNotBlank()) { refine() }
                    }
                }

                fields?.let { fs ->
                    val pinned = store.data.pinnedCreateFields
                    val shown = fs.filter { it.required || it.id in pinned }
                    val extra = fs.filterNot { it.required || it.id in pinned }
                    shown.forEach { FieldEditor(it, store, fieldValues, multiValues) }
                    if (extra.isNotEmpty()) {
                        Text(
                            "Дополнительные поля · ${extra.size}   ${if (extraOpen) "▲" else "▼"}",
                            color = forgeColors.inkMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { extraOpen = !extraOpen }.padding(vertical = 6.dp),
                        )
                        if (extraOpen) extra.forEach { FieldEditor(it, store, fieldValues, multiValues) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(if (status == CreateStatus.Running) "Создаётся…" else "Создать", enabled = status != CreateStatus.Running) { submit() }
                    Spacer(Modifier.width(14.dp))
                    StatusLine(status)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldEditor(
    field: CreateField,
    store: AppDataStore,
    values: SnapshotStateMap<String, String>,
    multi: SnapshotStateMap<String, Set<String>>,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(field.name + if (field.required) " *" else "", color = forgeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (!field.required) {
                val pinned = field.id in store.data.pinnedCreateFields
                Text(
                    if (pinned) "открепить" else "закрепить",
                    color = if (pinned) forgeColors.ember else forgeColors.inkFaint,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { store.togglePinnedField(field.id) }.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        when (field.control) {
            FieldControl.SELECT -> SearchPicker("", field.options.map { it.id to it.label }, values[field.id] ?: "") { values[field.id] = it }
            FieldControl.MULTISELECT -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                field.options.forEach { opt ->
                    val selected = (multi[field.id] ?: emptySet()).contains(opt.id)
                    OptionChip(opt.label, selected) {
                        val cur = multi[field.id] ?: emptySet()
                        multi[field.id] = if (selected) cur - opt.id else cur + opt.id
                    }
                }
            }
            FieldControl.LABELS -> OutlinedTextField(values[field.id] ?: "", { values[field.id] = it }, label = { Text("значения через запятую") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FieldControl.TEXTAREA -> OutlinedTextField(values[field.id] ?: "", { values[field.id] = it }, minLines = 2, modifier = Modifier.fillMaxWidth())
            else -> OutlinedTextField(values[field.id] ?: "", { values[field.id] = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(forgeColors.ember) else Modifier.border(1.dp, forgeColors.borderStrong, RoundedCornerShape(8.dp)))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) Color.White else forgeColors.inkMuted, fontSize = 12.sp)
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
