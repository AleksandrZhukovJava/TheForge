package com.forge.workshop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.theme.forgeColors

enum class PillStatus(val label: String) {
    IN_PROGRESS("In Progress"),
    TODO("To Do"),
    DONE("Done"),
    OPENED("opened"),
    DRAFT("draft"),
    MERGED("merged"),
    RUNNING("running"),
    FAILED("failed"),
    PASSED("passed"),
}

@Composable
fun StatusPill(status: PillStatus) {
    val color = pillColor(status)
    val neutral = status == PillStatus.TODO
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (neutral) Modifier.border(1.dp, forgeColors.borderStrong, RoundedCornerShape(999.dp))
                else Modifier.background(color.copy(alpha = 0.13f)),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun pillColor(status: PillStatus): Color = when (status) {
    PillStatus.IN_PROGRESS, PillStatus.RUNNING -> forgeColors.warn
    PillStatus.TODO -> forgeColors.inkMuted
    PillStatus.DONE, PillStatus.MERGED, PillStatus.PASSED -> forgeColors.good
    PillStatus.OPENED, PillStatus.DRAFT -> forgeColors.tool
    PillStatus.FAILED -> forgeColors.crit
}
