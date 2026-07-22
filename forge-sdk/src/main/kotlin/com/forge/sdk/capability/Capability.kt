package com.forge.sdk.capability

import com.forge.sdk.common.SemVer
import com.forge.sdk.common.TypeRef

/**
 * Descriptor of a capability a provider implements: its id, contract version, and typed IO.
 *
 * A provider advertises [Capability] descriptors (not bare ids) so the resolver can honour a
 * Strike's version constraint. [input]/[output] default to [TypeRef.ANY] to keep simple and
 * test providers lightweight.
 */
data class Capability(
    val id: CapabilityId,
    val version: SemVer = SemVer(1, 0, 0),
    val input: TypeRef = TypeRef.ANY,
    val output: TypeRef = TypeRef.ANY,
)
