package com.forge.workshop.sparks

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
import androidx.compose.foundation.shape.CircleShape
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
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.data.NType
import com.forge.workshop.data.NotificationEvent
import com.forge.workshop.theme.forgeColors

/**
 * Sparks — the event feed struck off the anvil: new tasks (ember) and status changes (steel).
 * Reading is a local overlay; nothing is written back to Jira.
 */
@Composable
fun SparksScreen(store: AppDataStore) {
    val events = store.data.notifications
    val unread = events.count { !it.read }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Sparks", color = forgeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (unread > 0) "$unread новых искр с наковальни" else "Искры от наковальни — события задач",
                    color = forgeColors.inkFaint,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (unread > 0) {
                Text(
                    "Прочитать всё",
                    color = forgeColors.ember,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { store.markNotificationsRead() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        if (events.isEmpty()) {
            Text(
                "Пока тихо — искры полетят, когда задача сменит статус или появится новая.",
                color = forgeColors.inkMuted,
                fontSize = 14.sp,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                events.forEach { SparkRow(it) }
            }
        }
    }
}

@Composable
private fun SparkRow(event: NotificationEvent) {
    val accent: Color = when (event.type) {
        NType.NEW -> forgeColors.ember
        NType.STATUS -> forgeColors.tool
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(forgeColors.surface2)
            .border(1.dp, if (event.read) forgeColors.border else forgeColors.borderStrong, RoundedCornerShape(10.dp))
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ember/steel dot — bright while unread, dimmed once read.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (event.read) accent.copy(alpha = 0.35f) else accent),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.issueKey, color = accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (event.type == NType.NEW) "НОВАЯ" else "СТАТУС",
                    color = forgeColors.inkFaint,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(event.summary, color = forgeColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Text(event.text, color = forgeColors.inkMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(relativeTime(event.at), color = forgeColors.inkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Compact «Х назад» stamp — kept coarse so it needs no live ticking. */
private fun relativeTime(at: Long): String {
    val secs = (System.currentTimeMillis() - at) / 1000
    return when {
        secs < 60 -> "только что"
        secs < 3600 -> "${secs / 60} мин"
        secs < 86_400 -> "${secs / 3600} ч"
        else -> "${secs / 86_400} дн"
    }
}
