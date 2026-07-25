package com.forge.workshop.recipe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.sdk.capability.DangerLevel
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.theme.ForgeColors
import com.forge.workshop.theme.forgeColors
import java.util.UUID
import kotlin.math.roundToInt

private const val NODE_W = 168f
private const val NODE_H = 58f

/** Accent per executor mode (цвет = смысл). */
private fun modeColor(mode: StrikeMode, c: ForgeColors): Color = when (mode) {
    StrikeMode.AUTO -> c.tool
    StrikeMode.MANUAL -> c.master
    StrikeMode.LLM_LOCAL -> c.smith
    StrikeMode.LLM_AGENT -> c.emberHot
}

/**
 * Node-graph recipe builder: palette → drag Strikes onto a dark grid board → connect with threads.
 * Fixed start/end anchors; click a node to configure its executor mode / confirmation; save + name.
 */
@Composable
fun RecipeBuilderScreen(
    store: AppDataStore,
    initial: SavedRecipe?,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "Новый рецепт") }
    val nodes = remember { mutableStateListOf<RecipeNode>().apply { addAll(initial?.nodes ?: defaultAnchors()) } }
    val links = remember { mutableStateListOf<RecipeLink>().apply { addAll(initial?.links ?: emptyList()) } }
    val recipeId = remember { initial?.id ?: UUID.randomUUID().toString() }
    var selected by remember { mutableStateOf<String?>(null) }
    var linkFrom by remember { mutableStateOf<String?>(null) }
    var addOffset by remember { mutableStateOf(0) }

    fun updateNode(id: String, transform: (RecipeNode) -> RecipeNode) {
        val i = nodes.indexOfFirst { it.id == id }
        if (i >= 0) nodes[i] = transform(nodes[i])
    }

    fun addFromPalette(type: StrikeType) {
        addOffset = (addOffset + 1) % 8
        nodes += RecipeNode(
            id = UUID.randomUUID().toString(),
            typeId = type.id,
            mode = type.allowedModes.first(),
            confirm = type.defaultDanger == DangerLevel.CONFIRM,
            x = 250f + addOffset * 26f,
            y = 90f + addOffset * 26f,
        )
    }

    fun onNodeTap(id: String) {
        val from = linkFrom
        if (from != null) {
            if (from != id && links.none { it.from == from && it.to == id }) links += RecipeLink(from, id)
            linkFrom = null
        } else {
            selected = id
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().background(forgeColors.surface2).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("← Foundry", forgeColors.borderStrong) { onBack() }
            Spacer(Modifier.width(14.dp))
            OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.width(280.dp))
            Spacer(Modifier.weight(1f))
            if (linkFrom != null) {
                Text("выберите цель нити…", color = forgeColors.emberHot, fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
            }
            PrimaryChip("Сохранить") {
                store.saveRecipe(SavedRecipe(recipeId, name.trim().ifBlank { "Рецепт" }, nodes.toList(), links.toList()))
                onBack()
            }
        }

        Row(Modifier.fillMaxSize()) {
            // Palette
            Column(
                modifier = Modifier.width(210.dp).fillMaxHeight().background(forgeColors.surface1).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ПАЛИТРА", color = forgeColors.inkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                StrikeCatalog.types.forEach { type -> PaletteItem(type) { addFromPalette(type) } }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(forgeColors.border))

            // Board
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(Color(0xFF0E0C0B))
                    .pointerInput(Unit) { detectTapGestures { selected = null; linkFrom = null } },
            ) {
                BoardCanvas(nodes, links)
                nodes.forEach { node ->
                    NodeCard(
                        node = node,
                        selected = node.id == selected,
                        pendingSource = node.id == linkFrom,
                        onDrag = { dx, dy -> updateNode(node.id) { it.copy(x = (it.x + dx).coerceAtLeast(0f), y = (it.y + dy).coerceAtLeast(0f)) } },
                        onTap = { onNodeTap(node.id) },
                    )
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(forgeColors.border))

            // Inspector
            Column(
                modifier = Modifier.width(270.dp).fillMaxHeight().background(forgeColors.surface1).verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val node = nodes.firstOrNull { it.id == selected }
                Inspector(
                    node = node,
                    outgoing = links.filter { it.from == node?.id },
                    startLink = { linkFrom = node?.id },
                    onMode = { m -> node?.let { n -> updateNode(n.id) { it.copy(mode = m) } } },
                    onConfirm = { c -> node?.let { n -> updateNode(n.id) { it.copy(confirm = c) } } },
                    onLinkCond = { link, cond ->
                        val i = links.indexOf(link); if (i >= 0) links[i] = link.copy(cond = cond)
                    },
                    onLinkDelete = { link -> links.remove(link) },
                    onDelete = {
                        node?.let { n ->
                            links.removeAll { it.from == n.id || it.to == n.id }
                            nodes.removeAll { it.id == n.id }
                            selected = null
                        }
                    },
                )
            }
        }
    }
}

/** Default fixed start/end nodes for a fresh recipe. */
private fun defaultAnchors(): List<RecipeNode> = listOf(
    RecipeNode(START_ID, START_ID, x = 60f, y = 60f),
    RecipeNode(END_ID, END_ID, x = 60f, y = 360f),
)

@Composable
private fun BoardCanvas(nodes: List<RecipeNode>, links: List<RecipeLink>) {
    val grid = forgeColors.border.copy(alpha = 0.35f)
    val thread = forgeColors.ember
    val byId = nodes.associateBy { it.id }
    Canvas(Modifier.fillMaxSize()) {
        val step = 26f
        var x = 0f
        while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }

        links.forEach { link ->
            val a = byId[link.from] ?: return@forEach
            val b = byId[link.to] ?: return@forEach
            val start = Offset(a.x + NODE_W, a.y + NODE_H / 2)
            val end = Offset(b.x, b.y + NODE_H / 2)
            val dx = (end.x - start.x).coerceAtLeast(40f) * 0.5f
            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(start.x + dx, start.y, end.x - dx, end.y, end.x, end.y)
            }
            drawPath(path, thread, style = Stroke(width = 2f))
            drawCircle(thread, radius = 3.5f, center = end)
        }
    }
}

@Composable
private fun NodeCard(
    node: RecipeNode,
    selected: Boolean,
    pendingSource: Boolean,
    onDrag: (Float, Float) -> Unit,
    onTap: () -> Unit,
) {
    val anchor = node.typeId == START_ID || node.typeId == END_ID
    val type = StrikeCatalog.byId(node.typeId)
    val title = when (node.typeId) {
        START_ID -> "СТАРТ"; END_ID -> "ФИНАЛ"; else -> type?.name ?: node.typeId
    }
    val accent = if (anchor) forgeColors.good else modeColor(node.mode, forgeColors)
    val borderColor = when {
        pendingSource -> forgeColors.emberHot
        selected -> forgeColors.ember
        else -> forgeColors.border
    }
    Box(
        modifier = Modifier
            .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
            .size(NODE_W.dp, NODE_H.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(forgeColors.surface2)
            .border(if (selected || pendingSource) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .pointerInput(node.id) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
            .pointerInput(node.id) { detectTapGestures { onTap() } }
            .padding(10.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Spacer(Modifier.width(7.dp))
                Text(title, color = forgeColors.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (!anchor) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(node.mode.label, color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    if (node.confirm) {
                        Spacer(Modifier.width(6.dp))
                        Text("⚑ confirm", color = forgeColors.warn, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun Inspector(
    node: RecipeNode?,
    outgoing: List<RecipeLink>,
    startLink: () -> Unit,
    onMode: (StrikeMode) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onLinkCond: (RecipeLink, EdgeCond) -> Unit,
    onLinkDelete: (RecipeLink) -> Unit,
    onDelete: () -> Unit,
) {
    Text("НАСТРОЙКА", color = forgeColors.inkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
    if (node == null) {
        Text("Выберите узел на доске.", color = forgeColors.inkMuted, fontSize = 13.sp)
        return
    }
    val anchor = node.typeId == START_ID || node.typeId == END_ID
    val type = StrikeCatalog.byId(node.typeId)
    Text(if (anchor) (if (node.typeId == START_ID) "Старт" else "Финал") else (type?.name ?: node.typeId), color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    type?.let { Text(it.description, color = forgeColors.inkMuted, fontSize = 12.sp) }

    Chip("+ нить отсюда", forgeColors.borderStrong) { startLink() }

    if (!anchor && type != null) {
        Spacer(Modifier.height(2.dp))
        Text("Исполнитель", color = forgeColors.inkFaint, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            type.allowedModes.forEach { m ->
                Toggle(m.label, node.mode == m, modeColor(m, forgeColors)) { onMode(m) }
            }
        }
        Toggle(if (node.confirm) "☑ Требует подтверждения" else "☐ Требует подтверждения", node.confirm, forgeColors.warn) { onConfirm(!node.confirm) }
    }

    if (outgoing.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("Нити", color = forgeColors.inkFaint, fontSize = 11.sp)
        outgoing.forEach { link ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val next = EdgeCond.entries[(link.cond.ordinal + 1) % EdgeCond.entries.size]
                Text(
                    "→ ${link.cond.label}",
                    color = forgeColors.inkMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onLinkCond(link, next) }.padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text("удалить", color = forgeColors.crit, fontSize = 11.sp, modifier = Modifier.clickable { onLinkDelete(link) }.padding(4.dp))
            }
        }
    }

    if (!anchor) {
        Spacer(Modifier.height(6.dp))
        Chip("Удалить узел", forgeColors.crit) { onDelete() }
    }
}

@Composable
private fun PaletteItem(type: StrikeType, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(9.dp)).clickable { onClick() }.padding(10.dp),
    ) {
        Text(type.name, color = forgeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            type.allowedModes.forEach { m ->
                Text(m.label, color = modeColor(m, forgeColors), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(7.dp))
            .then(if (on) Modifier.background(accent.copy(alpha = 0.18f)).border(1.dp, accent, RoundedCornerShape(7.dp)) else Modifier.border(1.dp, forgeColors.border, RoundedCornerShape(7.dp)))
            .clickable { onClick() }.padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, color = if (on) accent else forgeColors.inkMuted, fontSize = 11.sp)
    }
}

@Composable
private fun Chip(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, color, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrimaryChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(forgeColors.ember).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
