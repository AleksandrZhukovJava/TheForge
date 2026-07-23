package com.forge.sdk.policy

import com.forge.sdk.capability.CapabilityId

/** Window a quota is counted over. */
enum class QuotaWindow { PER_RUN, PER_SESSION }

/** "Не больше [max] × [capability]" within [window]. */
data class Quota(
    val capability: CapabilityId,
    val max: Int,
    val window: QuotaWindow = QuotaWindow.PER_RUN,
)

/**
 * The immutable rule set — the platform "Constitution".
 *
 * Loaded once by the platform from a trusted source; NOT modifiable by Skills, plugins or Smiths.
 * Every Strike is checked against it before execution, so no LLM can bypass it.
 */
data class Policy(
    /** Capabilities that must never run (e.g. gitlab.force-push). */
    val forbidden: Set<CapabilityId> = emptySet(),
    /** Capabilities that always require a Master confirmation. */
    val confirm: Set<CapabilityId> = emptySet(),
    /** Rate limits (e.g. max 1 jira.create-issue per run). */
    val quotas: List<Quota> = emptyList(),
) {
    companion object {
        val ALLOW_ALL = Policy()
    }
}

enum class PolicyDecision { ALLOW, DENY, CONFIRM }

data class PolicyVerdict(
    val decision: PolicyDecision,
    val reason: String? = null,
    val ruleId: String? = null,
) {
    companion object {
        val ALLOW = PolicyVerdict(PolicyDecision.ALLOW)
        fun deny(reason: String, ruleId: String) = PolicyVerdict(PolicyDecision.DENY, reason, ruleId)
        fun confirm(ruleId: String) = PolicyVerdict(PolicyDecision.CONFIRM, ruleId = ruleId)
    }
}
