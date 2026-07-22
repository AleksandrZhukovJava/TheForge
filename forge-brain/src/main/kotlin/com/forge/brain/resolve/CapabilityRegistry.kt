package com.forge.brain.resolve

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId

/**
 * The connective layer the spec left implicit: Strikes request abstract capabilities, providers
 * register the capabilities they implement, and the resolver picks one. Without this registry the
 * "rule of executor selection" cannot exist.
 */
interface CapabilityRegistry {

    /**
     * Registers (or replaces, idempotent by [ProviderId]) a provider.
     * @throws ForbiddenCapabilityException if the provider advertises a blacklisted capability —
     *   so a banned capability (e.g. GitLab merge / force-push) can never enter the registry.
     */
    fun register(provider: ExecutorProvider)

    fun unregister(id: ProviderId)

    /** All providers implementing [capability], in insertion order. */
    fun providersFor(capability: CapabilityId): List<ExecutorProvider>

    fun capabilities(): Set<CapabilityId>

    fun isForbidden(capability: CapabilityId): Boolean
}
