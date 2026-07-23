package com.forge.workshop.widget

import com.forge.workshop.ui.PillStatus

data class WRow(val code: String, val text: String, val status: PillStatus, val url: String? = null)

/** Long enough to demonstrate scroll-only-when-many. */
val jiraRows = listOf(
    WRow("FORGE-12", "StrikeResolver: версии", PillStatus.IN_PROGRESS),
    WRow("FORGE-14", "Workshop: нав-рейл", PillStatus.IN_PROGRESS),
    WRow("FORGE-15", "Grafana Tool", PillStatus.TODO),
    WRow("FORGE-16", "Model Router: бюджеты", PillStatus.TODO),
    WRow("FORGE-18", "Conveyor: автоапдейт", PillStatus.TODO),
    WRow("FORGE-19", "Anvil: кэш Stock", PillStatus.TODO),
    WRow("FORGE-9", "Secrets в Keychain", PillStatus.DONE),
    WRow("FORGE-7", "Capability Registry", PillStatus.DONE),
)

val mrRows = listOf(
    WRow("!41", "P3: safe integrations", PillStatus.OPENED),
    WRow("!44", "Anvil: минимальный Stock", PillStatus.DRAFT),
)

val pipelineRows = listOf(
    WRow("#1208", "!44 · compile", PillStatus.RUNNING),
    WRow("#1205", "!41 · test", PillStatus.FAILED),
)
