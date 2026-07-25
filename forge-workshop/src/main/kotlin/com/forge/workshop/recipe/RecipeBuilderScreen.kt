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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

// Board geometry, in board pixels (node.x/node.y are board pixels too).
private const val NODE_W = 172f
private const val NODE_H = 60f
private const val PAD = 13f      // room around the card for edge handles
private const val HANDLE = 16f

/** The four fixed connection points of a node. */
private enum class Port { TOP, BOTTOM, LEFT, RIGHT }

private fun portPos(n: RecipeNode, p: Port): Offset = when (p) {
    Port.TOP -> Offset(n.x + NODE_W / 2, n.y)
    Port.BOTTOM -> Offset(n.x + NODE_W / 2, n.y + NODE_H)
    Port.LEFT -> Offset(n.x, n.y + NODE_H / 2)
    Port.RIGHT -> Offset(n.x + NODE_W, n.y + NODE_H / 2)
}

private fun portNormal(p: Port): Offset = when (p) {
    Port.TOP -> Offset(0f, -1f)
    Port.BOTTOM -> Offset(0f, 1f)
    Port.LEFT -> Offset(-1f, 0f)
    Port.RIGHT -> Offset(1f, 0f)
}

/** Pick the exit/entry ports based on where B sits relative to A (dominant axis wins). */
private fun bestPorts(a: RecipeNode, b: RecipeNode): Pair<Port, Port> {
    val dx = (b.x + NODE_W / 2) - (a.x + NODE_W / 2)
    val dy = (b.y + NODE_H / 2) - (a.y + NODE_H / 2)
    return if (abs(dx) >= abs(dy)) {
        if (dx >= 0) Port.RIGHT to Port.LEFT else Port.LEFT to Port.RIGHT
    } else {
        if (dy >= 0) Port.BOTTOM to Port.TOP else Port.TOP to Port.BOTTOM
    }
}

private fun hit(n: RecipeNode, p: Offset): Boolean =
    p.x >= n.x && p.x <= n.x + NODE_W && p.y >= n.y && p.y <= n.y + NODE_H

private fun modeColor(mode: StrikeMode, c: ForgeColors): Color = when (mode) {
    StrikeMode.AUTO -> c.tool
    StrikeMode.MANUAL -> c.master
    StrikeMode.LLM_LOCAL -> c.smith
    StrikeMode.LLM_AGENT -> c.emberHot
}

/**
 * Node-graph recipe builder. Miro-style linking: hover a node → «+» handles on its four fixed
 * ports; press-drag from a handle onto another node to create a thread. Threads attach to the port
 * that faces the other node. Drag a node body to move it; click to configure it in the inspector.
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
    var addOffset by remember { mutableStateOf(0) }

    // Pending connection being dragged from a port handle.
    var pendingFrom by remember { mutableStateOf<String?>(null) }
    var pendingStart by remember { mutableStateOf<Offset?>(null) }
    var pendingPos by remember { mutableStateOf<Offset?>(null) }

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

    fun endConnection() {
        val pos = pendingPos
        val from = pendingFrom
        if (pos != null && from != null) {
            val target = nodes.firstOrNull { it.id != from && it.typeId != START_ID && hit(it, pos) }
            if (target != null && links.none { it.from == from && it.to == target.id }) {
                links += RecipeLink(from, target.id)
            }
        }
        pendingFrom = null; pendingStart = null; pendingPos = null
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
                    .pointerInput(Unit) { detectTapGestures { selected = null } },
            ) {
                BoardCanvas(nodes, links, pendingStart, pendingPos)
                nodes.forEach { node ->
                    NodeCard(
                        node = node,
                        selected = node.id == selected,
                        onDrag = { dx, dy -> updateNode(node.id) { it.copy(x = (it.x + dx).coerceAtLeast(0f), y = (it.y + dy).coerceAtLeast(0f)) } },
                        onTap = { selected = node.id },
                        onConnectStart = { port -> pendingFrom = node.id; pendingStart = portPos(node, port); pendingPos = portPos(node, port) },
                        onConnectDrag = { d -> pendingPos = pendingPos?.plus(d) },
                        onConnectEnd = { endConnection() },
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
                    onMode = { m -> node?.let { n -> updateNode(n.id) { it.copy(mode = m) } } },
                    onConfirm = { c -> node?.let { n -> updateNode(n.id) { it.copy(confirm = c) } } },
                    onLinkCond = { link, cond -> val i = links.indexOf(link); if (i >= 0) links[i] = link.copy(cond = cond) },
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

private fun defaultAnchors(): List<RecipeNode> = listOf(
    RecipeNode(START_ID, START_ID, x = 80f, y = 70f),
    RecipeNode(END_ID, END_ID, x = 80f, y = 360f),
)

@Composable
private fun BoardCanvas(nodes: List<RecipeNode>, links: List<RecipeLink>, pendingStart: Offset?, pendingPos: Offset?) {
    val grid = forgeColors.border.copy(alpha = 0.35f)
    val thread = forgeColors.ember
    val byId = nodes.associateBy { it.id }
    Canvas(Modifier.fillMaxSize()) {
        val stepPx = 26f
        var x = 0f
        while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += stepPx }
        var y = 0f
        while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += stepPx }

        links.forEach { link ->
            val a = byId[link.from] ?: return@forEach
            val b = byId[link.to] ?: return@forEach
            val (pa, pb) = bestPorts(a, b)
            val start = portPos(a, pa)
            val end = portPos(b, pb)
            val curve = (hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat() * 0.4f).coerceIn(40f, 130f)
            val c1 = start + portNormal(pa) * curve
            val c2 = end + portNormal(pb) * curve
            val path = Path().apply { moveTo(start.x, start.y); cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y) }
            drawPath(path, thread, style = Stroke(width = 2f))
            drawCircle(thread, radius = 3.5f, center = end)
        }

        // Live thread being dragged from a handle.
        if (pendingStart != null && pendingPos != null) {
            drawLine(thread.copy(alpha = 0.8f), pendingStart, pendingPos, 2f)
            drawCircle(thread, radius = 4f, center = pendingPos)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NodeCard(
    node: RecipeNode,
    selected: Boolean,
    onDrag: (Float, Float) -> Unit,
    onTap: () -> Unit,
    onConnectStart: (Port) -> Unit,
    onConnectDrag: (Offset) -> Unit,
    onConnectEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val anchor = node.typeId == START_ID || node.typeId == END_ID
    val type = StrikeCatalog.byId(node.typeId)
    val title = when (node.typeId) { START_ID -> "СТАРТ"; END_ID -> "ФИНАЛ"; else -> type?.name ?: node.typeId }
    val accent = if (anchor) forgeColors.good else modeColor(node.mode, forgeColors)
    var hovered by remember { mutableStateOf(false) }
    val showPorts = hovered && node.typeId != END_ID

    // Outer box gives room for the edge handles; positioned by board px.
    Box(
        modifier = Modifier
            .offset { IntOffset((node.x - PAD).roundToInt(), (node.y - PAD).roundToInt()) }
            .size(with(density) { (NODE_W + 2 * PAD).toDp() }, with(density) { (NODE_H + 2 * PAD).toDp() })
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
    ) {
        // The card
        Box(
            modifier = Modifier
                .offset { IntOffset(PAD.roundToInt(), PAD.roundToInt()) }
                .size(with(density) { NODE_W.toDp() }, with(density) { NODE_H.toDp() })
                .clip(RoundedCornerShape(10.dp))
                .background(forgeColors.surface2)
                .border(if (selected) 2.dp else 1.dp, if (selected) forgeColors.ember else forgeColors.border, RoundedCornerShape(10.dp))
                .pointerInput(node.id) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } }
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

        // The four fixed port handles (Miro-style), shown on hover.
        if (showPorts) {
            Port.entries.forEach { port -> PortHandle(port, density, onConnectStart, onConnectDrag, onConnectEnd) }
        }
    }
}

/** A draggable «+» handle at one of the four fixed ports; press-drag to pull a thread. */
@Composable
private fun PortHandle(
    port: Port,
    density: androidx.compose.ui.unit.Density,
    onConnectStart: (Port) -> Unit,
    onConnectDrag: (Offset) -> Unit,
    onConnectEnd: () -> Unit,
) {
    // Center of the port within the outer box (card origin at PAD,PAD).
    val cx = PAD + when (port) { Port.LEFT -> 0f; Port.RIGHT -> NODE_W; else -> NODE_W / 2 }
    val cy = PAD + when (port) { Port.TOP -> 0f; Port.BOTTOM -> NODE_H; else -> NODE_H / 2 }
    Box(
        modifier = Modifier
            .offset { IntOffset((cx - HANDLE / 2).roundToInt(), (cy - HANDLE / 2).roundToInt()) }
            .size(with(density) { HANDLE.toDp() })
            .clip(CircleShape)
            .background(forgeColors.ember)
            .pointerInput(port) {
                detectDragGestures(
                    onDragStart = { onConnectStart(port) },
                    onDrag = { change, drag -> change.consume(); onConnectDrag(drag) },
                    onDragEnd = { onConnectEnd() },
                    onDragCancel = { onConnectEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Inspector(
    node: RecipeNode?,
    outgoing: List<RecipeLink>,
    onMode: (StrikeMode) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onLinkCond: (RecipeLink, EdgeCond) -> Unit,
    onLinkDelete: (RecipeLink) -> Unit,
    onDelete: () -> Unit,
) {
    Text("НАСТРОЙКА", color = forgeColors.inkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
    if (node == null) {
        Text("Выберите узел на доске. Наведите на узел и потяните «+», чтобы соединить.", color = forgeColors.inkMuted, fontSize = 13.sp)
        return
    }
    val anchor = node.typeId == START_ID || node.typeId == END_ID
    val type = StrikeCatalog.byId(node.typeId)
    Text(if (anchor) (if (node.typeId == START_ID) "Старт" else "Финал") else (type?.name ?: node.typeId), color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    type?.let { Text(it.description, color = forgeColors.inkMuted, fontSize = 12.sp) }

    if (!anchor && type != null) {
        Spacer(Modifier.height(2.dp))
        Text("Исполнитель", color = forgeColors.inkFaint, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            type.allowedModes.forEach { m -> Toggle(m.label, node.mode == m, modeColor(m, forgeColors)) { onMode(m) } }
        }
        Toggle(if (node.confirm) "☑ Требует подтверждения" else "☐ Требует подтверждения", node.confirm, forgeColors.warn) { onConfirm(!node.confirm) }
    }

    if (outgoing.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("Нити", color = forgeColors.inkFaint, fontSize = 11.sp)
        outgoing.forEach { link ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val next = EdgeCond.entries[(link.cond.ordinal + 1) % EdgeCond.entries.size]
                Text("→ ${link.cond.label}", color = forgeColors.inkMuted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onLinkCond(link, next) }.padding(horizontal = 6.dp, vertical = 4.dp))
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
            type.allowedModes.forEach { m -> Text(m.label, color = modeColor(m, forgeColors), fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
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
