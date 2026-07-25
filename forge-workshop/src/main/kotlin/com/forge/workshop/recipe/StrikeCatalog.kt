package com.forge.workshop.recipe

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.capability.ExecutorKind
import kotlinx.serialization.Serializable

/**
 * How a Strike is executed — the user picks one per node (within the type's [StrikeType.allowedModes]).
 * Maps to an engine [ExecutorKind]; LLM modes additionally get the skill catalog (S2).
 */
@Serializable
enum class StrikeMode(val label: String, val kind: ExecutorKind) {
    AUTO("Авто", ExecutorKind.TOOL),
    MANUAL("Ручной", ExecutorKind.MASTER),
    LLM_LOCAL("Локальная LLM", ExecutorKind.SMITH),
    LLM_AGENT("Агент", ExecutorKind.SMITH),
}

/** A ready-made action we ship — a building block for recipes. */
data class StrikeType(
    val id: String,
    val name: String,
    val description: String,
    val capability: CapabilityId,
    val allowedModes: List<StrikeMode>,
    val defaultDanger: DangerLevel = DangerLevel.SAFE,
)

/**
 * The palette of built-in Strike types. Grows over time; later merges capabilities discovered from
 * the registry. The user drags these onto the board to compose a Recipe.
 */
object StrikeCatalog {
    val types: List<StrikeType> = listOf(
        StrikeType(
            "jira.create-issue", "Создать задачу Jira",
            "Создаёт задачу в Jira. Авто — по полям; локальная LLM — формулирует по описанию (+ скилы).",
            CapabilityId("jira.create-issue"), listOf(StrikeMode.AUTO, StrikeMode.LLM_LOCAL), DangerLevel.CONFIRM,
        ),
        StrikeType(
            "gitlab.open-mr", "Открыть MR",
            "Открывает merge request в GitLab. Merge/force-push запрещены сводом правил.",
            CapabilityId("gitlab.open-merge-request"), listOf(StrikeMode.AUTO), DangerLevel.CONFIRM,
        ),
        StrikeType(
            "code.write", "Написать код",
            "Пишет код по задаче. Агент Claude Code или локальная LLM; результат можно поправить вручную.",
            CapabilityId("code.write"), listOf(StrikeMode.LLM_AGENT, StrikeMode.LLM_LOCAL),
        ),
        StrikeType(
            "mr.review", "Ревью MR",
            "Ревью merge request. Агент или локальная LLM — как отдельные режимы одного страйка.",
            CapabilityId("review.generate"), listOf(StrikeMode.LLM_AGENT, StrikeMode.LLM_LOCAL),
        ),
        StrikeType(
            "service.explain", "Explain Service",
            "Объясняет сервис по коду и документации.",
            CapabilityId("explain.generate"), listOf(StrikeMode.LLM_LOCAL, StrikeMode.LLM_AGENT),
        ),
        StrikeType(
            "obs.investigate", "Investigate Production Error",
            "Тянет алерты/метрики и ищет причину инцидента.",
            CapabilityId("obs.analyze"), listOf(StrikeMode.LLM_LOCAL, StrikeMode.LLM_AGENT),
        ),
        StrikeType(
            "manual.action", "Ручное действие",
            "Ручной шаг: правки, решение или проверка человеком посреди флоу.",
            CapabilityId("manual.action"), listOf(StrikeMode.MANUAL), DangerLevel.CONFIRM,
        ),
    )

    fun byId(id: String): StrikeType? = types.firstOrNull { it.id == id }
}
