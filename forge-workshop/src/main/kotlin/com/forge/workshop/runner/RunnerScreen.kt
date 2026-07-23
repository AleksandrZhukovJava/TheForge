package com.forge.workshop.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.brain.execute.StrikeExecutor
import com.forge.brain.execute.StrikeOutcome
import com.forge.brain.policy.DefaultPolicy
import com.forge.brain.policy.PolicyEngine
import com.forge.brain.resolve.StrikeResolver
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.context.Stock
import com.forge.sdk.master.ConfirmRequest
import com.forge.workshop.foundry.SkillSpec
import com.forge.workshop.theme.forgeColors
import com.forge.workshop.ui.execColor

private enum class RunPhase { RUNNING, DONE, STOPPED }
private enum class StepStatus { WAIT, RUNNING, CONFIRM, DONE, STOPPED }

@Composable
fun RunnerScreen(skill: SkillSpec, onBack: () -> Unit, onFinished: (Boolean) -> Unit) {
    val gate = remember { UiMasterGate() }
    val demo = remember(skill.title) { buildDemoRun(skill.title) }
    val policy = remember(demo) { PolicyEngine(DefaultPolicy) }
    val statuses = remember(demo) {
        mutableStateListOf<StepStatus>().apply { repeat(demo.steps.size) { add(StepStatus.WAIT) } }
    }
    val logs = remember(demo) { mutableStateListOf<String>() }
    var phase by remember(demo) { mutableStateOf(RunPhase.RUNNING) }

    LaunchedEffect(demo) {
        policy.beginRun()
        val executor = StrikeExecutor(StrikeResolver(demo.registry), gate, policy)
        logs.add("Собираю Stock и запускаю Recipe…")
        for (i in demo.steps.indices) {
            val step = demo.steps[i]
            statuses[i] = if (step.decl.danger == DangerLevel.CONFIRM) StepStatus.CONFIRM else StepStatus.RUNNING
            when (val outcome = executor.run(step.decl, Stock.EMPTY)) {
                is StrikeOutcome.Done -> {
                    statuses[i] = StepStatus.DONE
                    logs.add("✓ ${step.name} — ${step.executorLabel}")
                }
                is StrikeOutcome.Rejected -> {
                    statuses[i] = StepStatus.STOPPED
                    logs.add("✗ ${step.name} — отклонено Master")
                    phase = RunPhase.STOPPED
                    onFinished(false)
                    return@LaunchedEffect
                }
                is StrikeOutcome.Blocked -> {
                    statuses[i] = StepStatus.STOPPED
                    logs.add("✗ ${step.name} — заблокировано: ${outcome.reason}")
                    phase = RunPhase.STOPPED
                    onFinished(false)
                    return@LaunchedEffect
                }
            }
        }
        phase = RunPhase.DONE
        logs.add("Готово.")
        onFinished(true)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChip(onBack)
                Spacer(Modifier.width(14.dp))
                Text(skill.title, color = forgeColors.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(10.dp))
                PhasePill(phase)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    demo.steps.forEachIndexed { i, step -> StrikeRow(step, statuses[i]) }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .background(forgeColors.surface2)
                        .border(1.dp, forgeColors.border, RoundedCornerShape(11.dp))
                        .padding(15.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("Ход выполнения", color = forgeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    logs.forEach { line ->
                        Text(line, color = forgeColors.inkMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        gate.pending?.let { request ->
            ConfirmModal(request, onApprove = { gate.answer(true) }, onReject = { gate.answer(false) })
        }
    }
}

@Composable
private fun StrikeRow(step: DemoStrike, status: StepStatus) {
    val stripe = execColor(step.tag)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
        Column(Modifier.weight(1f).padding(start = 14.dp, top = 11.dp, bottom = 11.dp)) {
            Text(step.name, color = forgeColors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(step.executorLabel, color = stripe, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        StatusChip(status)
        Spacer(Modifier.width(14.dp))
    }
}

@Composable
private fun StatusChip(status: StepStatus) {
    val label: String
    val color: Color
    when (status) {
        StepStatus.WAIT -> { label = "wait"; color = forgeColors.inkFaint }
        StepStatus.RUNNING -> { label = "running"; color = forgeColors.warn }
        StepStatus.CONFIRM -> { label = "confirm"; color = forgeColors.crit }
        StepStatus.DONE -> { label = "done"; color = forgeColors.good }
        StepStatus.STOPPED -> { label = "stopped"; color = forgeColors.crit }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BackChip(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, forgeColors.borderStrong, RoundedCornerShape(8.dp))
            .clickable { onBack() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text("← Foundry", color = forgeColors.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PhasePill(phase: RunPhase) {
    val label: String
    val color: Color
    when (phase) {
        RunPhase.RUNNING -> { label = "running"; color = forgeColors.warn }
        RunPhase.DONE -> { label = "done"; color = forgeColors.good }
        RunPhase.STOPPED -> { label = "stopped"; color = forgeColors.crit }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

// The Master confirmation modal now lives in ConfirmModal.kt (shared with the create flow).
