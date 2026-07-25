package com.forge.workshop.recipe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.brain.execute.StrikeExecutor
import com.forge.brain.execute.StrikeOutcome
import com.forge.brain.policy.DefaultPolicy
import com.forge.brain.policy.PolicyEngine
import com.forge.brain.resolve.DefaultCapabilityRegistry
import com.forge.brain.resolve.StrikeResolver
import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.domain.StrikeResult
import com.forge.sdk.domain.StrikeStatus
import com.forge.sdk.secret.SecretStore
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.llm.LlmClient
import com.forge.workshop.llm.LlmProfile
import com.forge.workshop.runner.ConfirmModal
import com.forge.workshop.runner.UiMasterGate
import com.forge.workshop.skills.SkillStore
import com.forge.workshop.smith.SmithExecutor
import com.forge.workshop.theme.ForgeColors
import com.forge.workshop.theme.forgeColors
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val RW = 172f
private const val RH = 60f

private enum class RunState { PENDING, RUNNING, DONE, FAILED, BLOCKED }

private fun rPortPos(n: RecipeNode, p: RPort): Offset = when (p) {
    RPort.TOP -> Offset(n.x + RW / 2, n.y)
    RPort.BOTTOM -> Offset(n.x + RW / 2, n.y + RH)
    RPort.LEFT -> Offset(n.x, n.y + RH / 2)
    RPort.RIGHT -> Offset(n.x + RW, n.y + RH / 2)
}

private enum class RPort { TOP, BOTTOM, LEFT, RIGHT }

private fun rPortNormal(p: RPort): Offset = when (p) {
    RPort.TOP -> Offset(0f, -1f); RPort.BOTTOM -> Offset(0f, 1f)
    RPort.LEFT -> Offset(-1f, 0f); RPort.RIGHT -> Offset(1f, 0f)
}

private fun rBestPorts(a: RecipeNode, b: RecipeNode): Pair<RPort, RPort> {
    val dx = (b.x + RW / 2) - (a.x + RW / 2)
    val dy = (b.y + RH / 2) - (a.y + RH / 2)
    return if (abs(dx) >= abs(dy)) {
        if (dx >= 0) RPort.RIGHT to RPort.LEFT else RPort.LEFT to RPort.RIGHT
    } else {
        if (dy >= 0) RPort.BOTTOM to RPort.TOP else RPort.TOP to RPort.BOTTOM
    }
}

/** Stub Tool for AUTO nodes until real Tool execution + input binding lands (R4). */
private class StubTool(private val cap: CapabilityId) : ExecutorProvider {
    override val id = ProviderId("stub.${cap.value}")
    override val kind = ExecutorKind.TOOL
    override val capabilities = setOf(Capability(cap))
    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        delay(300)
        return StrikeResult(strike.id, output = "ok")
    }
}

/** MANUAL node: the human does the work; the step just records completion (its CONFIRM gate paused). */
private class ManualProvider(private val cap: CapabilityId) : ExecutorProvider {
    override val id = ProviderId("manual.${cap.value}")
    override val kind = ExecutorKind.MASTER
    override val capabilities = setOf(Capability(cap))
    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult =
        StrikeResult(strike.id, output = "выполнено вручную")
}

/**
 * Runs a saved recipe on the board with live progress: step / auto / auto-to-point, highlighting
 * visited & current nodes, forks that ask which branch (others dimmed), and rollback to a past node.
 * Every step goes through the real StrikeExecutor (policy + Master); executors are stubs for now.
 */
@Composable
fun RecipeRunnerScreen(
    recipe: SavedRecipe,
    appData: AppDataStore,
    secrets: SecretStore,
    skillStore: SkillStore,
    onBack: () -> Unit,
) {
    val nodes = recipe.nodes
    val byId = remember(recipe) { nodes.associateBy { it.id } }
    val gate = remember { UiMasterGate() }
    val scope = rememberCoroutineScope()

    // Shared across the run: one policy (quotas persist over the whole recipe) and one LLM client.
    val policy = remember(recipe) { PolicyEngine(DefaultPolicy) }
    val http = remember { HttpClient(CIO) }
    DisposableEffect(Unit) { onDispose { http.close() } }
    val llm = remember { LlmClient(http) }

    /** Build the executor for one node, honouring its chosen mode (AUTO/MANUAL/LLM). */
    suspend fun executorFor(node: RecipeNode, cap: CapabilityId, goal: String): StrikeExecutor {
        val provider: ExecutorProvider = when (node.mode) {
            StrikeMode.MANUAL -> ManualProvider(cap)
            StrikeMode.LLM_LOCAL, StrikeMode.LLM_AGENT -> {
                val project = appData.data.skillProject
                val profile = LlmProfile(appData.data.llmBaseUrl.trimEnd('/'), appData.data.llmModel, secrets.get("llm.apikey"))
                val loadSkill: (String) -> String? = { name -> skillStore.list(project).firstOrNull { it.manifest.name == name }?.body }
                SmithExecutor(cap, llm, profile, skillStore.catalog(project), loadSkill, if (node.mode == StrikeMode.LLM_AGENT) "агент" else "локальная модель")
            }
            else -> StubTool(cap)
        }
        val registry = DefaultCapabilityRegistry().apply { register(provider) }
        return StrikeExecutor(StrikeResolver(registry), gate, policy)
    }

    val states = remember(recipe) { mutableStateMapOf<String, RunState>() }
    val path = remember(recipe) { mutableStateListOf<String>() }
    var currentId by remember(recipe) { mutableStateOf<String?>(null) }
    var forkOptions by remember(recipe) { mutableStateOf<List<String>?>(null) }
    var running by remember(recipe) { mutableStateOf(false) }
    var finished by remember(recipe) { mutableStateOf(false) }
    var autoTarget by remember(recipe) { mutableStateOf<String?>(null) }
    var pickTarget by remember(recipe) { mutableStateOf(false) }
    var note by remember(recipe) { mutableStateOf("") }

    val startId = remember(recipe) {
        nodes.firstOrNull { it.typeId == START_ID }?.id
            ?: nodes.firstOrNull { n -> recipe.links.none { it.to == n.id } }?.id
    }

    fun reset() {
        states.clear(); nodes.forEach { states[it.id] = RunState.PENDING }
        path.clear(); forkOptions = null; running = false; finished = false; autoTarget = null; pickTarget = false
        policy.beginRun()
        currentId = startId
        note = "готов к запуску"
    }
    LaunchedEffect(recipe) { reset() }

    fun eligible(node: RecipeNode, success: Boolean): List<String> =
        recipe.links.filter { it.from == node.id }.filter {
            when (it.cond) { EdgeCond.ALWAYS -> true; EdgeCond.ON_SUCCESS -> success; EdgeCond.ON_FAILURE -> !success }
        }.map { it.to }

    suspend fun advance() {
        val id = currentId ?: return
        if (forkOptions != null) return
        val node = byId[id] ?: return
        val anchor = node.typeId == START_ID || node.typeId == END_ID
        var success = true
        if (!anchor) {
            val type = StrikeCatalog.byId(node.typeId)
            if (type == null) { states[id] = RunState.FAILED; running = false; note = "нет типа страйка"; return }
            states[id] = RunState.RUNNING
            note = "${node.mode.label}: ${type.name}"
            val danger = if (node.confirm || node.mode == StrikeMode.MANUAL) DangerLevel.CONFIRM else DangerLevel.SAFE
            val goal = "${type.name}: ${type.description}"
            val executor = executorFor(node, type.capability, goal)
            val outcome = executor.run(StrikeDecl(StrikeId(node.id), type.capability, danger, input = mapOf("goal" to goal)), Stock.EMPTY)
            when (outcome) {
                is StrikeOutcome.Done -> {
                    success = outcome.result.status == StrikeStatus.OK
                    states[id] = if (success) RunState.DONE else RunState.FAILED
                }
                is StrikeOutcome.Rejected -> { states[id] = RunState.BLOCKED; running = false; note = "отклонено (Master)"; return }
                is StrikeOutcome.Blocked -> { states[id] = RunState.BLOCKED; running = false; note = "заблокировано: ${outcome.reason}"; return }
            }
        } else {
            states[id] = RunState.DONE
        }
        path.add(id)
        val succ = eligible(node, success)
        when {
            node.typeId == END_ID || succ.isEmpty() -> { currentId = null; finished = true; running = false; note = "рецепт завершён" }
            succ.size == 1 -> currentId = succ.first()
            else -> { forkOptions = succ; running = false; note = "развилка — выберите ветку" }
        }
    }

    fun stepOnce() { if (!finished && forkOptions == null) scope.launch { advance() } }

    fun startAuto(target: String?) {
        autoTarget = target; pickTarget = false; running = true
        scope.launch {
            while (running && !finished && forkOptions == null) {
                advance()
                if (autoTarget != null && path.lastOrNull() == autoTarget) { running = false; note = "дошли до точки"; break }
                delay(250)
            }
        }
    }

    fun onNodeClick(id: String) {
        val opts = forkOptions
        when {
            opts != null -> if (id in opts) { currentId = id; forkOptions = null; note = "ветка выбрана" }
            pickTarget -> startAuto(id)
            states[id] == RunState.DONE -> { // rollback
                val idx = path.indexOf(id)
                if (idx >= 0) {
                    path.drop(idx).forEach { states[it] = RunState.PENDING }
                    while (path.size > idx) path.removeAt(path.size - 1)
                    currentId = id; forkOptions = null; finished = false; running = false
                    policy.beginRun(); note = "откат к узлу"
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Control bar
        Row(
            modifier = Modifier.fillMaxWidth().background(forgeColors.surface2).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ctl("← назад", forgeColors.borderStrong) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(recipe.name, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(18.dp))
            if (!running) Ctl("▷ Шаг", forgeColors.tool, enabled = !finished && forkOptions == null) { stepOnce() }
            if (!running) Ctl("⏩ Авто", forgeColors.ember, enabled = !finished && forkOptions == null) { startAuto(null) }
            if (!running) Ctl("⤳ До точки", forgeColors.press, enabled = !finished && forkOptions == null) { pickTarget = true; note = "кликните узел-цель" }
            if (running) Ctl("⏸ Стоп", forgeColors.warn) { running = false; note = "пауза" }
            Ctl("↺ Сброс", forgeColors.borderStrong) { reset() }
            Spacer(Modifier.weight(1f))
            Text(note, color = forgeColors.inkMuted, fontSize = 12.sp)
        }

        Box(
            modifier = Modifier.fillMaxSize().clipToBounds().background(Color(0xFF0E0C0B)),
        ) {
            RunnerCanvas(nodes, recipe.links, states, currentId)
            nodes.forEach { node ->
                val st = states[node.id] ?: RunState.PENDING
                val clickable = when {
                    forkOptions != null -> node.id in forkOptions!!
                    pickTarget -> node.typeId != START_ID && node.typeId != END_ID
                    else -> st == RunState.DONE
                }
                RunNodeCard(node, st, node.id == currentId, clickable, forkOptions != null || pickTarget) { onNodeClick(node.id) }
            }
        }
    }

    gate.pending?.let { req -> ConfirmModal(req, onApprove = { gate.answer(true) }, onReject = { gate.answer(false) }) }
}

@Composable
private fun RunnerCanvas(nodes: List<RecipeNode>, links: List<RecipeLink>, states: Map<String, RunState>, currentId: String?) {
    val grid = forgeColors.border.copy(alpha = 0.35f)
    val byId = nodes.associateBy { it.id }
    val doneThread = forgeColors.good
    val idleThread = forgeColors.border
    Canvas(Modifier.fillMaxSize()) {
        var x = 0f; while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += 26f }
        var y = 0f; while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += 26f }
        links.forEach { link ->
            val a = byId[link.from] ?: return@forEach
            val b = byId[link.to] ?: return@forEach
            val (pa, pb) = rBestPorts(a, b)
            val start = rPortPos(a, pa); val end = rPortPos(b, pb)
            val traversed = states[link.from] == RunState.DONE && states[link.to] != RunState.PENDING
            val col = if (traversed) doneThread else idleThread
            val curve = (hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat() * 0.4f).coerceIn(40f, 130f)
            val c1 = start + rPortNormal(pa) * curve; val c2 = end + rPortNormal(pb) * curve
            val path = Path().apply { moveTo(start.x, start.y); cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y) }
            drawPath(path, col, style = Stroke(width = 2f))
            drawCircle(col, radius = 3.5f, center = end)
        }
    }
}

@Composable
private fun RunNodeCard(node: RecipeNode, state: RunState, current: Boolean, clickable: Boolean, selecting: Boolean, onClick: () -> Unit) {
    val density = LocalDensity.current
    val anchor = node.typeId == START_ID || node.typeId == END_ID
    val type = StrikeCatalog.byId(node.typeId)
    val title = when (node.typeId) { START_ID -> "СТАРТ"; END_ID -> "ФИНАЛ"; else -> type?.name ?: node.typeId }
    val (border, glow) = stateColors(state, current, forgeColors)
    val dim = selecting && !clickable
    Box(
        modifier = Modifier
            .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
            .size(with(density) { RW.toDp() }, with(density) { RH.toDp() })
            .clip(RoundedCornerShape(10.dp))
            .background(if (dim) forgeColors.surface1.copy(alpha = 0.4f) else forgeColors.surface2)
            .border(if (current) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
            .padding(10.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(glow))
                Spacer(Modifier.width(7.dp))
                Text(title, color = if (dim) forgeColors.inkFaint else forgeColors.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (!anchor) {
                Spacer(Modifier.height(6.dp))
                Text(stateLabel(state, node.mode), color = glow, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun stateColors(state: RunState, current: Boolean, c: ForgeColors): Pair<Color, Color> = when {
    current -> c.ember to c.ember
    state == RunState.DONE -> c.good to c.good
    state == RunState.RUNNING -> c.ember to c.ember
    state == RunState.FAILED -> c.crit to c.crit
    state == RunState.BLOCKED -> c.crit to c.crit
    else -> c.border to c.inkMuted
}

private fun stateLabel(state: RunState, mode: StrikeMode): String = when (state) {
    RunState.PENDING -> mode.label
    RunState.RUNNING -> "выполняется…"
    RunState.DONE -> "✓ готово"
    RunState.FAILED -> "✗ ошибка"
    RunState.BLOCKED -> "⛔ блок"
}

@Composable
private fun Ctl(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (enabled) color else forgeColors.border, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (enabled) color else forgeColors.inkFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
