package com.forge.brain.policy

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.policy.Policy
import com.forge.sdk.policy.PolicyVerdict
import com.forge.sdk.policy.QuotaWindow

/**
 * Enforces the [Policy] deterministically. The single gate every Strike passes through before it
 * runs — this is what makes the rule set unbreakable: it is code in the execution path, not a
 * request the model may or may not honour.
 */
class PolicyEngine(val policy: Policy) {

    private val runTally = HashMap<CapabilityId, Int>()
    private val sessionTally = HashMap<CapabilityId, Int>()

    /** Reset per-run counters at the start of a Skill run. */
    fun beginRun() {
        runTally.clear()
    }

    fun check(strike: StrikeDecl): PolicyVerdict {
        val cap = strike.capability

        if (cap in policy.forbidden) {
            return PolicyVerdict.deny("запрещено сводом правил", "forbidden/${cap.value}")
        }

        for (quota in policy.quotas) {
            if (quota.capability != cap) continue
            val used = tallyFor(quota.window)[cap] ?: 0
            if (used >= quota.max) {
                return PolicyVerdict.deny("лимит: не больше ${quota.max} × ${cap.value}", "quota/${cap.value}")
            }
        }

        if (cap in policy.confirm || strike.danger == DangerLevel.CONFIRM) {
            return PolicyVerdict.confirm("confirm/${cap.value}")
        }

        return PolicyVerdict.ALLOW
    }

    /** Record that a capability actually executed (drives quotas). */
    fun recordExecuted(capability: CapabilityId) {
        runTally.merge(capability, 1, Int::plus)
        sessionTally.merge(capability, 1, Int::plus)
    }

    private fun tallyFor(window: QuotaWindow): Map<CapabilityId, Int> =
        if (window == QuotaWindow.PER_RUN) runTally else sessionTally
}
