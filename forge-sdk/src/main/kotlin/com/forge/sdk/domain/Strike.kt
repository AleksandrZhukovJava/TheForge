package com.forge.sdk.domain

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.common.AnyVersion
import com.forge.sdk.common.VersionConstraint

/** Identifies a Strike within a Recipe. */
@JvmInline
value class StrikeId(val value: String)

/**
 * A Strike — the atomic operation (a hammer blow). Fully declarative: it states WHAT capability
 * it needs, never HOW it is executed. The resolver maps it to a concrete executor.
 */
data class StrikeDecl(
    val id: StrikeId,
    val capability: CapabilityId,
    val danger: DangerLevel = DangerLevel.SAFE,
    val versionConstraint: VersionConstraint = AnyVersion,
    /** Simplified input binding for now; typed bindings arrive with the Recipe engine (P2). */
    val input: Map<String, Any?> = emptyMap(),
)

enum class StrikeStatus { OK, FAILED }

/** Outcome of executing a Strike. */
data class StrikeResult(
    val strikeId: StrikeId,
    val output: Any? = null,
    val status: StrikeStatus = StrikeStatus.OK,
)
