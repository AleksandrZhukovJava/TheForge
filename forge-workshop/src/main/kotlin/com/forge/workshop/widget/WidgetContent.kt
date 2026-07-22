package com.forge.workshop.widget

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.nav.Spark
import com.forge.workshop.theme.forgeColors
import com.forge.workshop.ui.PillStatus
import com.forge.workshop.ui.StatusPill

/**
 * Standalone compact widget: a narrow bar by default, expands on hover to reveal cards.
 * The window is transparent, so the collapsed state shows only the bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WidgetPanel() {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(forgeColors.surface1)
                .border(1.dp, forgeColors.borderStrong, RoundedCornerShape(13.dp))
                .onPointerEvent(PointerEventType.Enter) { expanded = true }
                .onPointerEvent(PointerEventType.Exit) { expanded = false },
        ) {
            WidgetBar(expanded)
            AnimatedVisibility(expanded) { WidgetBody() }
        }
    }
}

@Composable
private fun WidgetBar(expanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spark(18.dp)
        Spacer(Modifier.width(9.dp))
        Text("The Forge", color = forgeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        MiniStat("6", "задач")
        Spacer(Modifier.width(9.dp))
        MiniStat("3", "MR")
        Spacer(Modifier.width(9.dp))
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(forgeColors.crit))
        Spacer(Modifier.width(9.dp))
        Text(if (expanded) "▲" else "▼", color = forgeColors.inkFaint, fontSize = 10.sp)
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, color = forgeColors.ink, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(3.dp))
        Text(label, color = forgeColors.inkMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun WidgetBody() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 11.dp, end = 11.dp, bottom = 11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        WidgetCard("Мои Jira-задачи", forgeColors.tool, "+ создать", jiraRows)
        WidgetCard("Мои Merge Requests", forgeColors.press, "+ открыть MR", mrRows)
        WidgetCard("Пайплайны", forgeColors.master, null, pipelineRows)
    }
}

@Composable
private fun WidgetCard(title: String, accent: Color, action: String?, rows: List<WRow>) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(11.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(9.dp))
            Text(title, color = forgeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (action != null) {
                Spacer(Modifier.weight(1f))
                Text(action, color = forgeColors.ember, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
            Column {
                rows.forEachIndexed { i, row ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 13.dp).size(1.dp).background(forgeColors.border))
                    WidgetRow(row)
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(row: WRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.code, color = forgeColors.inkMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(10.dp))
        Text(row.text, color = forgeColors.ink, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        StatusPill(row.status)
    }
}

/** The tray popover — separate from the Widget: quick glance + run. */
@Composable
fun TrayPopover(onRun: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(forgeColors.surface1)
                .border(1.dp, forgeColors.borderStrong, RoundedCornerShape(13.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spark(18.dp)
                Spacer(Modifier.width(10.dp))
                Text("The Forge", color = forgeColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().size(1.dp).background(forgeColors.border))
            Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrayStat("6", "задач")
                TrayStat("3", "MR")
                TrayStat("1", "упал CI", forgeColors.crit)
            }
            Column {
                jiraRows.take(3).forEach { WidgetRow(it) }
            }
            Box(Modifier.fillMaxWidth().size(1.dp).background(forgeColors.border))
            Box(Modifier.fillMaxWidth().padding(13.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(forgeColors.ember)
                        .clickable { onRun() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚒ Запустить Skill", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TrayStat(value: String, label: String, color: Color = forgeColors.ink) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(9.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = forgeColors.inkFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
