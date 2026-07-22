package com.forge.sdk.capability

import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeResult

/**
 * A concrete executor — a Tool, Press, Master or Smith — registered against one or more
 * capabilities. This is the single seam the whole platform plugs into: everything that can do
 * work implements it, and the resolver never knows anything more specific.
 */
interface ExecutorProvider {
    val id: ProviderId
    val kind: ExecutorKind
    val capabilities: Set<Capability>

    /** Tie-breaker among providers of the same [kind]; higher wins. */
    val priority: Int get() = 0

    /**
     * Preconditions on this Strike/Stock. Returning false removes this provider from the running,
     * so a Strike falls through to the next tier (possibly a Smith). Default: always applicable.
     */
    fun canHandle(strike: StrikeDecl, stock: Stock): Boolean = true

    suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult

    /** The descriptor this provider exposes for [capability], if any. */
    fun capabilityFor(capability: CapabilityId): Capability? =
        capabilities.firstOrNull { it.id == capability }
}
