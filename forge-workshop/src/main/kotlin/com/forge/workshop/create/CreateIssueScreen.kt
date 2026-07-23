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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** "Create Jira Story" — a real Skill launched from Foundry, run through the engine (policy + Master). */
@Composable
fun CreateIssueScreen(
    secrets: SecretStore,
    store: AppDataStore,
    onBack: () -> Unit,
    onFinished: (Boolean) -> Unit,
) {
    var project by remember { mutableStateOf(store.data.jiraProjectKey) }
    var summary by remember { mutableStateOf("") }
    var issueType by remember { mutableStateOf("Task") }
    var status by remember { mutableStateOf<CreateStatus>(CreateStatus.Idle) }
    val gate = remember { UiMasterGate() }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (project.isBlank() || summary.isBlank()) {
            status = CreateStatus.Failed("укажите проект и заголовок")
            return
        }
        store.setJiraProjectKey(project)
        scope.launch {
            status = CreateStatus.Running
            val base = secrets.get("jira.base-url")?.trimEnd('/')
            val token = secrets.get("jira.token")
            if (base == null || token == null) {
                status = CreateStatus.Failed("Jira не настроена — заполните Integrations")
                onFinished(false)
                return@launch
            }
            val email = secrets.get("jira.email")
            val auth = if (!email.isNullOrBlank()) JiraAuth.basic(email, token) else JiraAuth.bearer(token)
            val host = runCatching { java.net.URI(base).host }.getOrNull() ?: base
            val http = HttpClient(CIO)
            try {
                val registry = DefaultCapabilityRegistry().apply {
                    register(CreateJiraIssueTool(JiraClient(http, JiraConfig(base), auth), host))
                }
                val executor = StrikeExecutor(StrikeResolver(registry), gate, PolicyEngine(DefaultPolicy))
                val strike = StrikeDecl(
                    StrikeId("create"),
                    CapabilityId("jira.create-issue"),
                    DangerLevel.CONFIRM,
                    input = mapOf("project" to project, "summary" to summary, "issueType" to issueType),
                )
                status = when (val outcome = executor.run(strike, Stock.EMPTY)) {
                    is StrikeOutcome.Done -> {
                        val key = (outcome.result.output as? JiraIssueRef)?.key ?: "?"
                        onFinished(true)
                        CreateStatus.Success(key, "$base/browse/$key")
                    }
                    is StrikeOutcome.Rejected -> { onFinished(false); CreateStatus.Rejected }
                    is StrikeOutcome.Blocked -> { onFinished(false); CreateStatus.Failed(outcome.reason) }
                }
            } catch (e: Exception) {
                onFinished(false)
                status = CreateStatus.Failed(e.message ?: "ошибка создания")
            } finally {
                http.close()
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
                OutlinedTextField(project, { project = it }, label = { Text("Проект (key, напр. OPS)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(summary, { summary = it }, label = { Text("Заголовок") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(issueType, { issueType = it }, label = { Text("Тип задачи (Task / Story / Bug)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
