package com.forge.workshop.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.theme.forgeColors
import com.forge.workshop.ui.StatusPill

@Composable
fun HistoryScreen(history: HistoryStore) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("History", color = forgeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("Последние прогоны Skills", color = forgeColors.inkFaint, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        if (history.entries.isEmpty()) {
            Text("Пока пусто — запустите Skill из Foundry.", color = forgeColors.inkMuted, fontSize = 14.sp)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.entries.forEach { entry -> HistoryRow(entry) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.time, color = forgeColors.inkMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(14.dp))
        Text(entry.title, color = forgeColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        StatusPill(entry.status)
    }
}
