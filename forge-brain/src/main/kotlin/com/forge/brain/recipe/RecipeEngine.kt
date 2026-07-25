package com.forge.brain.recipe

import com.forge.brain.execute.StrikeExecutor
import com.forge.brain.execute.StrikeOutcome
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.EdgeCondition
import com.forge.sdk.domain.Recipe
import com.forge.sdk.domain.RecipeId
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.domain.StrikeStatus

/** Chooses which branch to follow when a node has more than one eligible outgoing edge. */
fun interface BranchChooser {
    suspend fun choose(from: StrikeId, options: List<StrikeId>): StrikeId

    companion object {
        /** Headless default — takes the first option (deterministic). UI supplies an interactive one. */
        val FIRST = BranchChooser { _, options -> options.first() }
    }
}

/** Result of running a single Strike within a Recipe. */
data class StrikeRunResult(val strikeId: StrikeId, val outcome: StrikeOutcome)

enum class RecipeStatus { COMPLETED, HALTED }

/** Outcome of running a whole Recipe: the steps in execution order plus a terminal status. */
data class RecipeRun(
    val recipeId: RecipeId,
    val steps: List<StrikeRunResult>,
    val status: RecipeStatus,
)

/**
 * Traverses a [Recipe]'s DAG, running each Strike through the real [StrikeExecutor] (policy + Master).
 *
 * - Outputs of finished Strikes are exposed to later Strikes via the [Stock] (key = strike id).
 * - Edge conditions gate the path: `ON_SUCCESS`/`ON_FAILURE` follow the last Strike's result.
 * - A fork (several eligible edges) is resolved by the [BranchChooser] — only one branch runs.
 * - A `Blocked`/`Rejected` Strike halts the run (status = HALTED).
 */
class RecipeEngine(
    private val executor: StrikeExecutor,
    private val chooser: BranchChooser = BranchChooser.FIRST,
) {

    suspend fun run(recipe: Recipe, seed: Stock = Stock.EMPTY): RecipeRun {
        executor.beginRun()

        val byId = recipe.strikes.associateBy { it.id }
        val outgoing = recipe.edges.groupBy { it.from }
        val hasIncoming = recipe.edges.map { it.to }.toSet()
        val start = recipe.strikes.firstOrNull { it.id !in hasIncoming } ?: recipe.strikes.firstOrNull()

        val outputs = LinkedHashMap<String, Any?>(seed.keys.associateWith { seed[it] })
        val steps = mutableListOf<StrikeRunResult>()
        val visited = mutableSetOf<StrikeId>()

        var current: StrikeDecl? = start
        while (current != null) {
            if (!visited.add(current.id)) break // guard against cycles

            val outcome = executor.run(current, Stock(outputs.toMap()))
            steps += StrikeRunResult(current.id, outcome)

            if (outcome is StrikeOutcome.Done) {
                outputs[current.id.value] = outcome.result.output
            } else {
                // Blocked or Rejected — the run stops here.
                return RecipeRun(recipe.id, steps, RecipeStatus.HALTED)
            }

            val success = outcome.result.status == StrikeStatus.OK
            val eligible = (outgoing[current.id] ?: emptyList()).filter { edge ->
                when (edge.condition) {
                    EdgeCondition.ALWAYS -> true
                    EdgeCondition.ON_SUCCESS -> success
                    EdgeCondition.ON_FAILURE -> !success
                }
            }
            current = when (eligible.size) {
                0 -> null // terminal node reached
                1 -> byId[eligible.first().to]
                else -> byId[chooser.choose(current.id, eligible.map { it.to })]
            }
        }
        return RecipeRun(recipe.id, steps, RecipeStatus.COMPLETED)
    }
}
