package com.forge.workshop.integrations

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.sdk.secret.SecretStore
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.data.TaskBlock
import com.forge.workshop.theme.forgeColors
import kotlinx.coroutines.launch

private data class Field(val label: String, val key: String, val secret: Boolean = false)

@Composable
fun IntegrationsScreen(
    secrets: SecretStore,
    refreshMinutes: Int,
    onIntervalChange: (Int) -> Unit,
    onSaved: () -> Unit,
    store: AppDataStore,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Integrations", color = forgeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Токены сохраняются локально (…/TheForge) и переживают перезапуск. Ввести нужно один раз.",
            color = forgeColors.inkFaint,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))

        RefreshCard(refreshMinutes, onIntervalChange)
        Spacer(Modifier.height(12.dp))
        BlocksCard(store)
        Spacer(Modifier.height(12.dp))

        IntegrationCard(
            title = "Jira",
            accent = forgeColors.tool,
            fields = listOf(
                Field("Base URL", "jira.base-url"),
                Field("Email", "jira.email"),
                Field("API token", "jira.token", secret = true),
            ),
            requiredKey = "jira.token",
            secrets = secrets,
            onSaved = onSaved,
        )
        Spacer(Modifier.height(12.dp))
        IntegrationCard(
            title = "GitLab",
            accent = forgeColors.press,
            fields = listOf(
                Field("Base URL", "gitlab.base-url"),
                Field("Personal access token", "gitlab.token", secret = true),
            ),
            requiredKey = "gitlab.token",
            secrets = secrets,
            onSaved = onSaved,
        )
    }
}

@Composable
private fun RefreshCard(refreshMinutes: Int, onIntervalChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(12.dp))
            .padding(18.dp),
    ) {
        Text("Обновление", color = forgeColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Как часто виджет тянет данные (опрос идёт только когда виджет виден).",
            color = forgeColors.inkFaint,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 5, 15, 30).forEach { m ->
                IntervalChip(m, selected = m == refreshMinutes) { onIntervalChange(m) }
            }
        }
    }
}

@Composable
private fun BlocksCard(store: AppDataStore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(12.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Блоки задач", color = forgeColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "сбросить",
                color = forgeColors.inkMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { store.resetBlocks() }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "＋ блок",
                color = forgeColors.ember,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { store.addBlock() }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Колонки на Bench: имя + статусы Jira через запятую. «Своя задача» = локальные задачи.",
            color = forgeColors.inkFaint,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        store.data.blocks.forEach { block ->
            BlockRow(block, store)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BlockRow(block: TaskBlock, store: AppDataStore) {
    var name by remember(block.id) { mutableStateOf(block.name) }
    var statuses by remember(block.id) { mutableStateOf(block.statuses.joinToString(", ")) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; store.setBlockName(block.id, it) },
            label = { Text("имя") },
            singleLine = true,
            modifier = Modifier.weight(0.9f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = statuses,
            onValueChange = { statuses = it; store.setBlockStatuses(block.id, it) },
            label = { Text("статусы через запятую") },
            singleLine = true,
            modifier = Modifier.weight(1.4f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "✕",
            color = forgeColors.crit,
            fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { store.deleteBlock(block.id) }.padding(8.dp),
        )
    }
}

@Composable
private fun IntervalChip(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Color.White else forgeColors.inkMuted
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.background(forgeColors.ember)
                else Modifier.border(1.dp, forgeColors.borderStrong, RoundedCornerShape(8.dp)),
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text("$minutes мин", color = fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IntegrationCard(
    title: String,
    accent: Color,
    fields: List<Field>,
    requiredKey: String,
    secrets: SecretStore,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf<String, String>() }
    var configured by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fields.forEach { f -> secrets.get(f.key)?.let { values[f.key] = it } }
        configured = secrets.get(requiredKey) != null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(12.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(10.dp))
            Text(title, color = forgeColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            ConfiguredPill(configured)
        }
        Spacer(Modifier.height(14.dp))
        fields.forEach { f ->
            OutlinedTextField(
                value = values[f.key] ?: "",
                onValueChange = { values[f.key] = it; configured = false },
                label = { Text(f.label) },
                singleLine = true,
                visualTransformation = if (f.secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SaveButton {
                scope.launch {
                    fields.forEach { f ->
                        val v = (values[f.key] ?: "").trim()
                        if (v.isNotEmpty()) secrets.put(f.key, v)
                    }
                    configured = secrets.get(requiredKey) != null
                    onSaved()
                }
            }
        }
    }
}

@Composable
private fun ConfiguredPill(configured: Boolean) {
    val color = if (configured) forgeColors.good else forgeColors.inkMuted
    val label = if (configured) "настроено" else "не настроено"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (configured) Modifier.background(color.copy(alpha = 0.13f))
                else Modifier.border(1.dp, forgeColors.borderStrong, RoundedCornerShape(999.dp)),
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SaveButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(forgeColors.ember)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text("Сохранить", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
