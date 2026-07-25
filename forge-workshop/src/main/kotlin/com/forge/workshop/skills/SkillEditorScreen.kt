package com.forge.workshop.skills

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.forge.workshop.theme.forgeColors

private const val TEMPLATE = """# <навык>

Опиши, ЧТО делать и ГДЕ искать информацию. Модель прочитает это, когда решит,
что навык подходит по описанию.

## Когда применять
- <ситуация>

## Как
- <шаг>
- <где смотреть: файл, страница Confluence, соглашение>
"""

/** Manual SKILL.md editor: name + description (the trigger the model sees) + markdown body. */
@Composable
fun SkillEditorScreen(
    store: SkillStore,
    project: String,
    initial: Skill?,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.manifest?.name ?: "") }
    var description by remember { mutableStateOf(initial?.manifest?.description ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: TEMPLATE) }
    var caps by remember { mutableStateOf(initial?.manifest?.allowedCapabilities?.joinToString(", ") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (name.isBlank()) { error = "укажите имя навыка"; return }
        val capList = caps.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (initial == null) {
            store.create(project, name, description, body, capList)
        } else {
            store.save(project, initial.id, SkillManifest(name.trim(), description.trim(), capList), body)
        }
        onBack()
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("← Навыки", forgeColors.borderStrong) { onBack() }
            Spacer(Modifier.width(14.dp))
            Text(if (initial == null) "Новый навык" else "Навык", color = forgeColors.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(10.dp))
            Text("проект: $project", color = forgeColors.inkFaint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.width(640.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Имя (name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description, { description = it },
                label = { Text("Описание (description) — по нему модель решает, брать ли навык") },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(caps, { caps = it }, label = { Text("Разрешённые capability (через запятую, опционально)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Тело навыка (Markdown)", color = forgeColors.inkFaint, fontSize = 11.sp)
            OutlinedTextField(body, { body = it }, minLines = 12, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryChip("Сохранить") { save() }
                error?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(it, color = forgeColors.crit, fontSize = 12.sp)
                }
            }
            Text("Сохранится как SKILL.md с frontmatter (name/description) и телом.", color = forgeColors.inkFaint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, color, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 11.dp, vertical = 7.dp),
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun PrimaryChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(forgeColors.ember).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}
