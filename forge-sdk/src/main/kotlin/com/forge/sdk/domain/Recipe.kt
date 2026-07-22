package com.forge.sdk.domain

/** Identifies a Recipe. */
@JvmInline
value class RecipeId(val value: String)

/** Condition on a Recipe edge. Linear Recipes use only [Always]; branches/error paths come later. */
enum class EdgeCondition { ALWAYS, ON_SUCCESS, ON_FAILURE }

/** A directed edge between two Strikes in the Recipe DAG. */
data class RecipeEdge(
    val from: StrikeId,
    val to: StrikeId,
    val condition: EdgeCondition = EdgeCondition.ALWAYS,
)

/**
 * A Recipe — the process a Skill follows, modelled as a DAG from day one (decision Q4).
 * A linear Recipe is just the degenerate case; the engine (P2) traverses this graph.
 */
data class Recipe(
    val id: RecipeId,
    val strikes: List<StrikeDecl>,
    val edges: List<RecipeEdge> = linear(strikes),
) {
    companion object {
        /** Chains Strikes head-to-tail — the linear default. */
        fun linear(strikes: List<StrikeDecl>): List<RecipeEdge> =
            strikes.zipWithNext { a, b -> RecipeEdge(a.id, b.id) }
    }
}
