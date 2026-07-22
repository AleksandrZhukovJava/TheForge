package com.forge.workshop.runner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.forge.sdk.master.ConfirmRequest
import com.forge.sdk.master.DecideRequest
import com.forge.sdk.master.DecideResult
import com.forge.sdk.master.MasterGate
import kotlinx.coroutines.CompletableDeferred

/**
 * A [MasterGate] backed by the Workshop UI. `confirm` publishes the pending request as observable
 * state (the modal renders it) and suspends until the user answers — the real P2 contract wired to
 * a real dialog, no engine changes.
 */
class UiMasterGate : MasterGate {

    var pending by mutableStateOf<ConfirmRequest?>(null)
        private set

    private var awaiting: CompletableDeferred<Boolean>? = null

    override suspend fun confirm(request: ConfirmRequest): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        awaiting = deferred
        pending = request
        return try {
            deferred.await()
        } finally {
            pending = null
            awaiting = null
        }
    }

    /** Called by the modal buttons. */
    fun answer(approved: Boolean) {
        awaiting?.complete(approved)
    }

    override suspend fun decide(request: DecideRequest): DecideResult = DecideResult("")
}
