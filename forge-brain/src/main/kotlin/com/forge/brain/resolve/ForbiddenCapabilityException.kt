package com.forge.brain.resolve

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ProviderId

/** Thrown when a provider tries to register a capability that is on the registry blacklist. */
class ForbiddenCapabilityException(
    val capability: CapabilityId,
    val provider: ProviderId,
) : RuntimeException(
    "Provider '${provider.value}' declares forbidden capability '${capability.value}'",
)
