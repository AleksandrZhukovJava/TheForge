package com.forge.executors

import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeResult
import com.forge.sdk.master.DecideRequest
import com.forge.sdk.master.MasterGate

/**
 * Executor of kind MASTER: the "work" is a human decision (resolve a conflict, choose a branch),
 * obtained through the [MasterGate]. The result carries the chosen value.
 *
 * @param promptFor builds the human-facing question from the Strike.
 */
class MasterExecutor(
    override val id: ProviderId,
    override val capabilities: Set<Capability>,
    private val gate: MasterGate,
    private val promptFor: (StrikeDecl) -> DecideRequest,
) : ExecutorProvider {

    override val kind: ExecutorKind = ExecutorKind.MASTER

    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        val decision = gate.decide(promptFor(strike))
        return StrikeResult(strike.id, output = decision.chosen)
    }
}
