package com.forge.brain.resolve

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId

/**
 * In-memory registry. Insertion order is preserved so listing is stable; the resolver imposes the
 * actual selection order, so correctness never depends on registration order.
 *
 * @param forbidden capabilities that may never be registered or resolved (from Forge Core security).
 */
class DefaultCapabilityRegistry(
    private val forbidden: Set<CapabilityId> = emptySet(),
) : CapabilityRegistry {

    private val providers = LinkedHashMap<ProviderId, ExecutorProvider>()

    override fun register(provider: ExecutorProvider) {
        val clash = provider.capabilities.map { it.id }.firstOrNull { it in forbidden }
        if (clash != null) throw ForbiddenCapabilityException(clash, provider.id)
        providers[provider.id] = provider
    }

    override fun unregister(id: ProviderId) {
        providers.remove(id)
    }

    override fun providersFor(capability: CapabilityId): List<ExecutorProvider> =
        providers.values.filter { p -> p.capabilities.any { it.id == capability } }

    override fun capabilities(): Set<CapabilityId> =
        providers.values.flatMapTo(mutableSetOf()) { p -> p.capabilities.map { it.id } }

    override fun isForbidden(capability: CapabilityId): Boolean = capability in forbidden
}
