package com.forge.brain.resolve

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId

/** The outcome of resolving a Strike to an executor. */
sealed interface Resolution {

    /** A provider was chosen. [requiresConfirmation] is true for CONFIRM-level Strikes. */
    data class Selected(
        val provider: ExecutorProvider,
        val requiresConfirmation: Boolean,
    ) : Resolution

    /** The capability is banned (blacklisted, or the Strike itself is FORBIDDEN). */
    data class Forbidden(
        val capability: CapabilityId,
        val reason: String,
    ) : Resolution

    /** No registered provider can handle the capability under the current Stock. */
    data class NoExecutor(
        val capability: CapabilityId,
    ) : Resolution
}

/**
 * Audit signal: more than one provider tied for the top slot (same kind and priority). Selection
 * stays deterministic (by provider id), but the ambiguity is worth surfacing.
 */
data class AmbiguousProviderEvent(
    val capability: CapabilityId,
    val kind: ExecutorKind,
    val priority: Int,
    val contenders: List<ProviderId>,
    val chosen: ProviderId,
)
