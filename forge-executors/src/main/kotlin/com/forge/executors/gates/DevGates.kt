package com.forge.executors.gates

import com.forge.sdk.master.ConfirmRequest
import com.forge.sdk.master.DecideRequest
import com.forge.sdk.master.DecideResult
import com.forge.sdk.master.MasterGate

/** Dev/test gate: approves every confirmation and picks the first offered option. */
object AutoApproveGate : MasterGate {
    override suspend fun confirm(request: ConfirmRequest): Boolean = true
    override suspend fun decide(request: DecideRequest): DecideResult =
        DecideResult(request.options.firstOrNull().orEmpty())
}

/** Dev/test gate: rejects every confirmation; used to verify the deny path. */
object RejectingGate : MasterGate {
    override suspend fun confirm(request: ConfirmRequest): Boolean = false
    override suspend fun decide(request: DecideRequest): DecideResult =
        error("RejectingGate makes no decisions")
}
