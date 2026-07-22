package com.forge.workshop.foundry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.theme.forgeColors

@Composable
fun FoundryScreen(onRun: (SkillSpec) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Foundry", color = forgeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("инженерные возможности · проект the-forge", color = forgeColors.inkFaint, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sampleSkills) { skill -> SkillCard(skill, onRun) }
        }
    }
}

@Composable
private fun SkillCard(skill: SkillSpec, onRun: (SkillSpec) -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(12.dp))
            .padding(15.dp),
    ) {
        Text(skill.title, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(skill.description, color = forgeColors.inkMuted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            skill.executors.forEach { tag ->
                ExecChip(tag)
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            RunButton { onRun(skill) }
        }
    }
}

@Composable
private fun ExecChip(tag: ExecTag) {
    val c = tag.color()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(tag.label, color = c, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RunButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(forgeColors.ember)
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text("Запустить", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExecTag.color(): Color = when (this) {
    ExecTag.TOOL -> forgeColors.tool
    ExecTag.PRESS -> forgeColors.press
    ExecTag.MASTER -> forgeColors.master
    ExecTag.SMITH -> forgeColors.smith
}
