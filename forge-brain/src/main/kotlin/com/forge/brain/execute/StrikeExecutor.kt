package com.forge.brain.execute

import com.forge.brain.policy.PolicyEngine
import com.forge.brain.resolve.Resolution
import com.forge.brain.resolve.StrikeResolver
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.domain.StrikeResult
import com.forge.sdk.master.ConfirmRequest
import com.forge.sdk.master.MasterGate
import com.forge.sdk.policy.Policy
import com.forge.sdk.policy.PolicyDecision

/**
 * Executes a single Strike end-to-end: resolve → (confirm via Master if required) → execute.
 *
 * The Recipe engine (later phase) will drive many of these across the DAG; here we nail the
 * one-Strike contract, including the Master confirmation gate for CONFIRM-level Strikes.
 */
class StrikeExecutor(
    private val resolver: StrikeResolver,
    private val masterGate: MasterGate,
    private val policy: PolicyEngine = PolicyEngine(Policy.ALLOW_ALL),
) {

    suspend fun run(strike: StrikeDecl, stock: Stock): StrikeOutcome {
        // Rule set first: no action runs without clearing the policy gate.
        val verdict = policy.check(strike)
        if (verdict.decision == PolicyDecision.DENY) {
            return StrikeOutcome.Blocked(strike.id, verdict.reason ?: "запрещено сводом правил")
        }

        return when (val resolution = resolver.resolve(strike, stock)) {
            is Resolution.Forbidden ->
                StrikeOutcome.Blocked(strike.id, resolution.reason)

            is Resolution.NoExecutor ->
                StrikeOutcome.Blocked(strike.id, "no executor for '${resolution.capability.value}'")

            is Resolution.Selected -> {
                val needsConfirmation = verdict.decision == PolicyDecision.CONFIRM ||
                    strike.danger == DangerLevel.CONFIRM
                val approved = !needsConfirmation || masterGate.confirm(
                    ConfirmRequest(
                        strikeId = strike.id,
                        capability = strike.capability,
                        summary = "${strike.capability.value} via ${resolution.provider.id.value}",
                    ),
                )
                if (!approved) {
                    StrikeOutcome.Rejected(strike.id)
                } else {
                    val result = resolution.provider.execute(strike, stock)
                    policy.recordExecuted(strike.capability)
                    StrikeOutcome.Done(result)
                }
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
