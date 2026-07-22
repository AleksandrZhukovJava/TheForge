package com.forge.brain.resolve.fakes

import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.common.SemVer
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeResult

/** Configurable [ExecutorProvider] for resolver/registry tests. */
class FakeProvider(
    id: String,
    override val kind: ExecutorKind,
    capability: CapabilityId,
    version: SemVer = SemVer(1, 0, 0),
    override val priority: Int = 0,
    private val canHandleFn: (StrikeDecl, Stock) -> Boolean = { _, _ -> true },
) : ExecutorProvider {

    override val id: ProviderId = ProviderId(id)
    override val capabilities: Set<Capability> = setOf(Capability(capability, version))

    override fun canHandle(strike: StrikeDecl, stock: Stock): Boolean = canHandleFn(strike, stock)

    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult =
        StrikeResult(strike.id, output = "${id}:done")
}
