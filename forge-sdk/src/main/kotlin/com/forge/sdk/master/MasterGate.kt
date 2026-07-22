package com.forge.sdk.master

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.domain.StrikeId

/**
 * The seam through which the platform reaches the human — the "Master".
 *
 * Two distinct human touchpoints, one interface:
 *  - [confirm]: gate a side-effecting Strike (danger = CONFIRM) before it runs;
 *  - [decide]: a MASTER-kind capability whose "work" IS a human decision.
 *
 * Hosts implement it: the Workshop shows a dialog (P4), a CLI prompts stdin, tests script answers.
 * The engine stays headless and never blocks on UI directly.
 */
interface MasterGate {

    suspend fun confirm(request: ConfirmRequest): Boolean

    suspend fun decide(request: DecideRequest): DecideResult
}

data class ConfirmRequest(
    val strikeId: StrikeId,
    val capability: CapabilityId,
    val summary: String,
)

data class DecideRequest(
    val strikeId: StrikeId,
    val capability: CapabilityId,
    val prompt: String,
    val options: List<String> = emptyList(),
)

data class DecideResult(val chosen: String)
