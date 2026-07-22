package com.forge.sdk.capability

/** Identifies an engineering capability, e.g. "vcs.open-merge-request". A Strike requests one. */
@JvmInline
value class CapabilityId(val value: String)

/** Identifies a concrete executor provider, e.g. "gitlab.open-mr". Unique within a registry. */
@JvmInline
value class ProviderId(val value: String)
