package com.forge.brain.resolve.fakes

import com.forge.sdk.master.ConfirmRequest
import com.forge.sdk.master.DecideRequest
import com.forge.sdk.master.DecideResult
import com.forge.sdk.master.MasterGate

/** Records confirmation requests and answers them with a fixed decision. */
class FakeGate(private val approve: Boolean = true) : MasterGate {

    val confirms = mutableListOf<ConfirmRequest>()

    override suspend fun confirm(request: ConfirmRequest): Boolean {
        confirms += request
        return approve
    }

    override suspend fun decide(request: DecideRequest): DecideResult =
        DecideResult(request.options.firstOrNull().orEmpty())
}
