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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.theme.forgeColors

/**
 * Skills library — classic Agent-Skills: SKILL.md memos, one folder per project. Progressive
 * disclosure: cards show name + description; expand to read the body. Manual create/edit/delete.
 */
@Composable
fun SkillsScreen(
    appData: AppDataStore,
    skillStore: SkillStore,
    onNew: () -> Unit,
    onEdit: (Skill) -> Unit,
) {
    val project = appData.data.skillProject
    var refresh by remember { mutableStateOf(0) }
    val skills = remember(project, refresh) { skillStore.list(project) }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Skills", color = forgeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Навыки-памятки (SKILL.md) — знания для LLM-шагов, альтернатива RAG", color = forgeColors.inkFaint, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            ProjectSelector(project, skillStore.projects(), onPick = { appData.setSkillProject(it) }, onCreate = { skillStore.ensureProject(it); appData.setSkillProject(it) })
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(forgeColors.ember).clickable { onNew() }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text("+ Новый навык", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(18.dp))

        if (skills.isEmpty()) {
            Text("В проекте «$project» пока нет навыков — создайте первый.", color = forgeColors.inkMuted, fontSize = 14.sp)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                skills.forEach { skill ->
                    SkillCard(
                        skill = skill,
                        open = expanded == skill.id,
                        onToggle = { expanded = if (expanded == skill.id) null else skill.id },
                        onEdit = { onEdit(skill) },
                        onDelete = { skillStore.delete(project, skill.id); refresh++ },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectSelector(project: String, projects: List<String>, onPick: (String) -> Unit, onCreate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Box {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, forgeColors.borderStrong, RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("проект: $project ▾", color = forgeColors.inkMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false; creating = false }) {
            projects.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { onPick(p); open = false }) }
            if (creating) {
                DropdownMenuItem(
                    text = {
                        OutlinedTextField(newName, { newName = it }, placeholder = { Text("имя проекта", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.width(180.dp))
                    },
                    onClick = {},
                )
                DropdownMenuItem(text = { Text("создать «${newName.ifBlank { "…" }}»", color = forgeColors.ember) }, onClick = {
                    if (newName.isNotBlank()) { onCreate(newName.trim()); open = false; creating = false; newName = "" }
                })
            } else {
                DropdownMenuItem(text = { Text("＋ новый проект", color = forgeColors.ember) }, onClick = { creating = true })
            }
        }
    }
}

@Composable
private fun SkillCard(skill: Skill, open: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(10.dp)).padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onToggle() }) {
                Text(skill.manifest.name, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(skill.manifest.description.ifBlank { "(без описания)" }, color = forgeColors.inkMuted, fontSize = 12.sp)
            }
            Text(if (open) "свернуть" else "читать", color = forgeColors.smith, fontSize = 12.sp, modifier = Modifier.clickable { onToggle() }.padding(8.dp))
            Text("править", color = forgeColors.ember, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onEdit() }.padding(8.dp))
            Text("удалить", color = forgeColors.crit, fontSize = 12.sp, modifier = Modifier.clickable { onDelete() }.padding(8.dp))
        }
        if (skill.manifest.allowedCapabilities.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("capability: ${skill.manifest.allowedCapabilities.joinToString(", ")}", color = forgeColors.inkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(forgeColors.surface1).padding(13.dp)) {
                Text(skill.body, color = forgeColors.inkMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
