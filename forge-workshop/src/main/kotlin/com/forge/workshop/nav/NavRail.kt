package com.forge.workshop.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.theme.forgeColors

enum class NavItem(val label: String) {
    BENCH("Bench"),
    FOUNDRY("Foundry"),
    SPARKS("Sparks"),
    HISTORY("History"),
    INTEGRATIONS("Integrations"),
}

@Composable
fun NavRail(selected: NavItem, onSelect: (NavItem) -> Unit, unread: Int = 0) {
    Column(
        modifier = Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(forgeColors.surface2)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spark(20.dp)
            Spacer(Modifier.width(10.dp))
            Text("The Forge", color = forgeColors.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        NavItem.entries.forEach { item ->
            NavRow(item, item == selected, if (item == NavItem.SPARKS) unread else 0) { onSelect(item) }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box24Avatar()
            Spacer(Modifier.width(10.dp))
            Text("Aleksandr · 2", color = forgeColors.inkFaint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NavRow(item: NavItem, active: Boolean, badge: Int, onClick: () -> Unit) {
    val fg = if (active) forgeColors.ember else forgeColors.inkMuted
    val bg = if (active) forgeColors.ember.copy(alpha = 0.12f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIcon(item, fg)
        Spacer(Modifier.width(11.dp))
        Text(
            item.label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
        if (badge > 0) {
            Spacer(Modifier.weight(1f))
            UnreadBadge(badge)
        }
    }
}

/** Ember pill with the unread Sparks count. */
@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(forgeColors.ember)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = forgeColors.ground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun Spark(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(Brush.linearGradient(listOf(forgeColors.emberHot, forgeColors.ember))),
    )
}

@Composable
private fun Box24Avatar() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(forgeColors.master, forgeColors.tool))),
    )
}

@Composable
private fun NavIcon(item: NavItem, tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val s = size.minDimension
        when (item) {
            NavItem.BENCH -> {
                // workbench: a top and two legs
                drawLine(tint, Offset(s * 0.14f, s * 0.4f), Offset(s * 0.86f, s * 0.4f), s * 0.11f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.27f, s * 0.42f), Offset(s * 0.27f, s * 0.82f), s * 0.09f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.73f, s * 0.42f), Offset(s * 0.73f, s * 0.82f), s * 0.09f, StrokeCap.Round)
            }
            NavItem.FOUNDRY -> {
                val cell = s * 0.34f
                val a = s * 0.13f
                val b = s * 0.53f
                val r = CornerRadius(s * 0.06f)
                listOf(a to a, b to a, a to b, b to b).forEach { (x, y) ->
                    drawRoundRect(tint, topLeft = Offset(x, y), size = Size(cell, cell), cornerRadius = r)
                }
            }
            NavItem.HISTORY -> {
                drawCircle(tint, radius = s * 0.4f, style = Stroke(width = s * 0.09f))
                drawLine(tint, center, Offset(center.x, center.y - s * 0.26f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
                drawLine(tint, center, Offset(center.x + s * 0.2f, center.y + s * 0.05f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
            }
            NavItem.INTEGRATIONS -> {
                val st = Stroke(width = s * 0.09f)
                val rr = CornerRadius(s * 0.13f)
                drawRoundRect(tint, topLeft = Offset(s * 0.09f, s * 0.3f), size = Size(s * 0.46f, s * 0.4f), cornerRadius = rr, style = st)
                drawRoundRect(tint, topLeft = Offset(s * 0.45f, s * 0.3f), size = Size(s * 0.46f, s * 0.4f), cornerRadius = rr, style = st)
            }
            NavItem.SPARKS -> {
                // a four-point spark (star) struck off the anvil
                val c = center
                val long = s * 0.42f
                val short = s * 0.13f
                val path = Path().apply {
                    moveTo(c.x, c.y - long)
                    lineTo(c.x + short, c.y - short)
                    lineTo(c.x + long, c.y)
                    lineTo(c.x + short, c.y + short)
                    lineTo(c.x, c.y + long)
                    lineTo(c.x - short, c.y + short)
                    lineTo(c.x - long, c.y)
                    lineTo(c.x - short, c.y - short)
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}
