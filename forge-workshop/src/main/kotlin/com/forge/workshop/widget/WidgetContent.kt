package com.forge.workshop.widget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.dashboard.DashboardData
import com.forge.workshop.dashboard.DashboardState
import com.forge.workshop.nav.Spark
import com.forge.workshop.theme.forgeColors
import com.forge.workshop.ui.PillStatus
import com.forge.workshop.ui.StatusPill

/**
 * Standalone compact widget: a narrow bar by default, expands on hover to reveal cards.
 * Data comes from [DashboardState]; the bar shows live counts and the expanded view scrolls
 * long lists only when they overflow.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WidgetPanel(
    state: DashboardState,
    onRefresh: () -> Unit,
    onMoveBy: (Int, Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    // Fill the whole window so the surface covers it exactly — the window itself shrinks when
    // collapsed (driven from Main), so there's no leftover transparent area to catch clicks or show
    // a dark residue.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(13.dp))
            .background(forgeColors.surface1)
            .border(1.dp, forgeColors.borderStrong, RoundedCornerShape(13.dp))
            .onPointerEvent(PointerEventType.Enter) { onExpandedChange(true) }
            .onPointerEvent(PointerEventType.Exit) { onExpandedChange(false) },
    ) {
        WidgetBar(
            state = state,
            expanded = expanded,
            onRefresh = onRefresh,
            dragModifier = Modifier.pointerInput(Unit) {
                var last: java.awt.Point? = null
                detectDragGestures(
                    onDragStart = { last = java.awt.MouseInfo.getPointerInfo()?.location },
                    onDragEnd = { last = null },
                    onDragCancel = { last = null },
                    onDrag = { change, _ ->
                        change.consume()
                        val cur = java.awt.MouseInfo.getPointerInfo()?.location
                        val prev = last
                        if (cur != null && prev != null) onMoveBy(cur.x - prev.x, cur.y - prev.y)
                        if (cur != null) last = cur
                    },
                )
            },
        )
        if (expanded) WidgetBody(state)
    }
}

private fun failing(data: DashboardData): Int = data.pipelines.count { it.status == PillStatus.FAILED }

@Composable
private fun WidgetBar(state: DashboardState, expanded: Boolean, onRefresh: () -> Unit, dragModifier: Modifier) {
    val data = (state as? DashboardState.Loaded)?.data
    Row(
        modifier = dragModifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spark(18.dp)
        Spacer(Modifier.width(9.dp))
        Text("The Forge", color = forgeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        MiniStat(data?.jira?.size?.toString() ?: "–", "задач")
        Spacer(Modifier.width(9.dp))
        MiniStat(data?.mrs?.size?.toString() ?: "–", "MR")
        if (data != null && failing(data) > 0) {
            Spacer(Modifier.width(9.dp))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(forgeColors.crit))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "⟳",
            color = forgeColors.inkMuted,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onRefresh() }.padding(2.dp),
        )
        Spacer(Modifier.width(6.dp))
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
private fun WidgetBody(state: DashboardState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (state) {
            is DashboardState.Loaded -> {
                val d = state.data
                if (d.jira.isNotEmpty()) WidgetCard("Мои Jira-задачи", forgeColors.tool, "+ создать", d.jira)
                if (d.mrs.isNotEmpty()) WidgetCard("Мои Merge Requests", forgeColors.press, "+ открыть MR", d.mrs)
                if (d.pipelines.isNotEmpty()) WidgetCard("Пайплайны", forgeColors.master, null, d.pipelines)
                if (d.jira.isEmpty() && d.mrs.isEmpty() && d.pipelines.isEmpty()) HintLine("нет активных задач и MR")
                Text("обновлено ${state.updatedAt}", color = forgeColors.inkFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            DashboardState.Loading -> HintLine("загрузка…")
            is DashboardState.Error -> HintLine("ошибка: ${state.message}", forgeColors.crit)
            DashboardState.NotConfigured -> HintLine("подключите Jira/GitLab в Integrations")
        }
    }
}

@Composable
private fun HintLine(text: String, color: Color = forgeColors.inkMuted) {
    Text(text, color = color, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(title, color = forgeColors.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (action != null) {
                Spacer(Modifier.weight(1f))
                Text(action, color = forgeColors.ember, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetRow(row: WRow) {
    TooltipArea(
        delayMillis = 250,
        tooltip = { RowTooltip(row) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (row.url != null) Modifier.clickable { openInBrowser(row.url) } else Modifier)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.code, color = forgeColors.inkMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text(
                row.text,
                color = forgeColors.ink,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Floating card shown over the widget on hover — full title + a hint to click through. */
@Composable
private fun RowTooltip(row: WRow) {
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.borderStrong, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.code, color = forgeColors.ember, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            StatusPill(row.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(row.text, color = forgeColors.ink, fontSize = 13.sp)
        if (row.url != null) {
            Spacer(Modifier.height(6.dp))
            Text("клик — открыть ↗", color = forgeColors.inkFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun openInBrowser(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {
        // browser unavailable — ignore
    }
}

/** The tray popover — separate from the Widget: quick glance + run. */
@Composable
fun TrayPopover(state: DashboardState, onRun: () -> Unit, onRefresh: () -> Unit) {
    val data = (state as? DashboardState.Loaded)?.data
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
                Spacer(Modifier.weight(1f))
                Text(
                    "⟳",
                    color = forgeColors.inkMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onRefresh() }.padding(2.dp),
                )
            }
            Box(Modifier.fillMaxWidth().size(1.dp).background(forgeColors.border))
            Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrayStat(data?.jira?.size?.toString() ?: "–", "задач")
                TrayStat(data?.mrs?.size?.toString() ?: "–", "MR")
                TrayStat(data?.let { failing(it).toString() } ?: "–", "упал CI", forgeColors.crit)
            }
            Column {
                (data?.jira ?: emptyList()).take(3).forEach { WidgetRow(it) }
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
