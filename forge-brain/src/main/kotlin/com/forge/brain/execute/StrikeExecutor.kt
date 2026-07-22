package com.forge.brain.execute

import com.forge.brain.resolve.Resolution
import com.forge.brain.resolve.StrikeResolver
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.domain.StrikeResult
import com.forge.sdk.master.ConfirmRequest
import com.forge.sdk.master.MasterGate

/**
 * Executes a single Strike end-to-end: resolve → (confirm via Master if required) → execute.
 *
 * The Recipe engine (later phase) will drive many of these across the DAG; here we nail the
 * one-Strike contract, including the Master confirmation gate for CONFIRM-level Strikes.
 */
class StrikeExecutor(
    private val resolver: StrikeResolver,
    private val masterGate: MasterGate,
) {

    suspend fun run(strike: StrikeDecl, stock: Stock): StrikeOutcome =
        when (val resolution = resolver.resolve(strike, stock)) {
            is Resolution.Forbidden ->
                StrikeOutcome.Blocked(strike.id, resolution.reason)

            is Resolution.NoExecutor ->
                StrikeOutcome.Blocked(strike.id, "no executor for '${resolution.capability.value}'")

            is Resolution.Selected -> {
                val approved = !resolution.requiresConfirmation || masterGate.confirm(
                    ConfirmRequest(
                        strikeId = strike.id,
                        capability = strike.capability,
                        summary = "${strike.capability.value} via ${resolution.provider.id.value}",
                    ),
                )
                if (!approved) {
                    StrikeOutcome.Rejected(strike.id)
                } else {
                    StrikeOutcome.Done(resolution.provider.execute(strike, stock))
                }
            }
        }
}

/** The result of attempting to execute a Strike. */
sealed interface StrikeOutcome {
    data class Done(val result: StrikeResult) : StrikeOutcome
    data class Rejected(val strikeId: StrikeId) : StrikeOutcome
    data class Blocked(val strikeId: StrikeId, val reason: String) : StrikeOutcome
}
