package com.forge.brain.resolve

import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl

/**
 * Maps an abstract Strike to a concrete executor — the heart of the engine.
 *
 * The selection rule is Automation-First and fully deterministic:
 *   1. reject forbidden capabilities;
 *   2. keep providers that satisfy the version constraint AND [ExecutorProvider.canHandle];
 *   3. pick the minimum by (kind order asc, priority desc, provider id asc).
 *
 * Because [com.forge.sdk.capability.ExecutorKind.order] drives step 3, a Smith (LLM) is chosen only
 * when no Tool, Press or Master applies.
 *
 * @param onAmbiguity invoked when the top slot is tied (same kind + priority); default: ignore.
 */
class StrikeResolver(
    private val registry: CapabilityRegistry,
    private val onAmbiguity: (AmbiguousProviderEvent) -> Unit = {},
) {

    private val order: Comparator<ExecutorProvider> =
        compareBy({ it.kind.order }, { -it.priority }, { it.id.value })

    fun resolve(strike: StrikeDecl, stock: Stock): Resolution {
        val capability = strike.capability

        if (strike.danger == DangerLevel.FORBIDDEN) {
            return Resolution.Forbidden(capability, "Strike is declared FORBIDDEN")
        }
        if (registry.isForbidden(capability)) {
            return Resolution.Forbidden(capability, "Capability is blacklisted")
        }

        val candidates = registry.providersFor(capability)
            .filter { satisfiesVersion(it, strike) }
            .filter { it.canHandle(strike, stock) }

        if (candidates.isEmpty()) {
            return Resolution.NoExecutor(capability)
        }

        val chosen = candidates.minWith(order)

        val tied = candidates.filter { it.kind == chosen.kind && it.priority == chosen.priority }
        if (tied.size > 1) {
            onAmbiguity(
                AmbiguousProviderEvent(
                    capability = capability,
                    kind = chosen.kind,
                    priority = chosen.priority,
                    contenders = tied.map { it.id }.sortedBy { it.value },
                    chosen = chosen.id,
                ),
            )
        }

        return Resolution.Selected(
            provider = chosen,
            requiresConfirmation = strike.danger == DangerLevel.CONFIRM,
        )
    }

    private fun satisfiesVersion(provider: ExecutorProvider, strike: StrikeDecl): Boolean {
        val capability = provider.capabilityFor(strike.capability) ?: return false
        return strike.versionConstraint.allows(capability.version)
    }
}
