package com.forge.brain.policy

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.policy.Policy
import com.forge.sdk.policy.PolicyDecision
import com.forge.sdk.policy.Quota
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyEngineTest {

    private val push = CapabilityId("gitlab.force-push")
    private val create = CapabilityId("jira.create-issue")
    private val read = CapabilityId("jira.load-issue")

    private fun strike(cap: CapabilityId, danger: DangerLevel = DangerLevel.SAFE) =
        StrikeDecl(StrikeId("s"), cap, danger)

    @Test fun `forbidden capability is denied`() {
        val engine = PolicyEngine(Policy(forbidden = setOf(push)))
        assertEquals(PolicyDecision.DENY, engine.check(strike(push)).decision)
    }

    @Test fun `quota allows up to the limit then denies`() {
        val engine = PolicyEngine(Policy(quotas = listOf(Quota(create, max = 1))))
        assertEquals(PolicyDecision.ALLOW, engine.check(strike(create)).decision)
        engine.recordExecuted(create)
        assertEquals(PolicyDecision.DENY, engine.check(strike(create)).decision)
    }

    @Test fun `beginRun resets per-run quota`() {
        val engine = PolicyEngine(Policy(quotas = listOf(Quota(create, max = 1))))
        engine.recordExecuted(create)
        assertEquals(PolicyDecision.DENY, engine.check(strike(create)).decision)
        engine.beginRun()
        assertEquals(PolicyDecision.ALLOW, engine.check(strike(create)).decision)
    }

    @Test fun `capability in confirm set requires confirmation`() {
        val engine = PolicyEngine(Policy(confirm = setOf(create)))
        assertEquals(PolicyDecision.CONFIRM, engine.check(strike(create)).decision)
    }

    @Test fun `danger CONFIRM on the strike requires confirmation`() {
        val engine = PolicyEngine(Policy.ALLOW_ALL)
        assertEquals(PolicyDecision.CONFIRM, engine.check(strike(create, DangerLevel.CONFIRM)).decision)
    }

    @Test fun `otherwise allowed`() {
        val engine = PolicyEngine(Policy.ALLOW_ALL)
        assertEquals(PolicyDecision.ALLOW, engine.check(strike(read)).decision)
    }
}
