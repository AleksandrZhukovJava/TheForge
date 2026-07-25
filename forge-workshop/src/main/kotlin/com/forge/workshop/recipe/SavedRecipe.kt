package com.forge.workshop.recipe

import kotlinx.serialization.Serializable

/** Special node type ids for the fixed start/end anchors of every recipe. */
const val START_ID = "__start__"
const val END_ID = "__end__"

/** Branch condition on a link (mirrors the engine's EdgeCondition, kept serializable & UI-local). */
@Serializable
enum class EdgeCond(val label: String) { ALWAYS("всегда"), ON_SUCCESS("при успехе"), ON_FAILURE("при ошибке") }

/**
 * A node on the board = one Strike instance (or the fixed start/end anchor). Carries its board
 * coordinates so the graph layout persists.
 */
@Serializable
data class RecipeNode(
    val id: String,
    val typeId: String,
    val mode: StrikeMode = StrikeMode.AUTO,
    val confirm: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
    /** Static per-node inputs (project, summary, …) that feed the real Tool call. */
    val inputs: Map<String, String> = emptyMap(),
)

/** A thread between two nodes on the board. */
@Serializable
data class RecipeLink(val from: String, val to: String, val cond: EdgeCond = EdgeCond.ALWAYS)

/** A user-authored recipe: the graph (nodes + links) plus board layout, saved and named. */
@Serializable
data class SavedRecipe(
    val id: String,
    val name: String,
    val nodes: List<RecipeNode> = emptyList(),
    val links: List<RecipeLink> = emptyList(),
) {
    val start: RecipeNode? get() = nodes.firstOrNull { it.typeId == START_ID }
    val end: RecipeNode? get() = nodes.firstOrNull { it.typeId == END_ID }
}
