package com.forge.workshop.weld

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.forge.workshop.dashboard.DashboardState
import com.forge.workshop.theme.forgeColors
import com.forge.workshop.ui.StatusPill
import com.forge.workshop.widget.WRow

/** Weld — merge requests on my review (a merge = joining two pieces). Separated from Bench. */
@Composable
fun WeldScreen(state: DashboardState, onRefresh: () -> Unit) {
    val mrs = (state as? DashboardState.Loaded)?.data?.mrs.orEmpty()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weld", color = forgeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(12.dp))
            Text("merge requests на моём ревью", color = forgeColors.inkFaint, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "⟳ обновить",
                color = forgeColors.inkMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, forgeColors.borderStrong, RoundedCornerShape(8.dp)).clickable { onRefresh() }.padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        when (state) {
            is DashboardState.Loaded ->
                if (mrs.isEmpty()) Hint("на ревью пусто")
                else Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { mrs.forEach { MrCard(it) } }
            DashboardState.Loading -> Hint("загрузка…")
            is DashboardState.Error -> Hint("ошибка: ${state.message}", forgeColors.crit)
            DashboardState.NotConfigured -> Hint("подключите GitLab в Настройках → Интеграции")
        }
    }
}

@Composable
private fun MrCard(row: WRow) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(11.dp)).padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.code, color = forgeColors.inkMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            StatusPill(row.status, row.statusName ?: row.status.label)
        }
        Spacer(Modifier.height(7.dp))
        Text(row.text, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (row.url != null) {
            Spacer(Modifier.height(10.dp))
            Text("открыть ↗", color = forgeColors.ember, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { openInBrowser(row.url) })
        }
    }
}

@Composable
private fun Hint(text: String, color: Color = forgeColors.inkMuted) {
    Text(text, color = color, fontSize = 14.sp)
}

private fun openInBrowser(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {
        // browser unavailable — ignore
    }
}
