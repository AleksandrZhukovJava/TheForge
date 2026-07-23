package com.forge.brain.execute

import com.forge.brain.policy.PolicyEngine
import com.forge.brain.resolve.DefaultCapabilityRegistry
import com.forge.brain.resolve.StrikeResolver
import com.forge.brain.resolve.fakes.FakeGate
import com.forge.brain.resolve.fakes.FakeProvider
import com.forge.sdk.policy.Policy
import com.forge.sdk.policy.Quota
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.capability.ExecutorKind.TOOL
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StrikeExecutorTest {

    private val X = CapabilityId("cap.x")

    private fun strike(danger: DangerLevel = DangerLevel.SAFE) =
        StrikeDecl(StrikeId("s1"), X, danger)

    private fun executor(gate: FakeGate, forbidden: Set<CapabilityId> = emptySet()): Pair<StrikeExecutor, DefaultCapabilityRegistry> {
        val registry = DefaultCapabilityRegistry(forbidden)
        return StrikeExecutor(StrikeResolver(registry), gate) to registry
    }

    @Test fun `safe strike executes without asking the Master`() = runBlocking {
        val gate = FakeGate()
        val (exec, reg) = executor(gate)
        reg.register(FakeProvider("tool", TOOL, X))
        val outcome = exec.run(strike(), Stock.EMPTY)
        assertIs<StrikeOutcome.Done>(outcome)
        assertEquals("tool:done", outcome.result.output)
        assertTrue(gate.confirms.isEmpty())
    }

    @Test fun `confirm strike executes after Master approves`() = runBlocking {
        val gate = FakeGate(approve = true)
        val (exec, reg) = executor(gate)
        reg.register(FakeProvider("tool", TOOL, X))
        val outcome = exec.run(strike(DangerLevel.CONFIRM), Stock.EMPTY)
        assertIs<StrikeOutcome.Done>(outcome)
        assertEquals(1, gate.confirms.size)
    }

    @Test fun `confirm strike is rejected when Master declines`() = runBlocking {
        val gate = FakeGate(approve = false)
        val (exec, reg) = executor(gate)
        reg.register(FakeProvider("tool", TOOL, X))
        val outcome = exec.run(strike(DangerLevel.CONFIRM), Stock.EMPTY)
        assertIs<StrikeOutcome.Rejected>(outcome)
        assertEquals(1, gate.confirms.size)
    }

    @Test fun `forbidden strike is blocked`() = runBlocking {
        val gate = FakeGate()
        val (exec, _) = executor(gate, forbidden = setOf(X))
        val outcome = exec.run(strike(), Stock.EMPTY)
        assertIs<StrikeOutcome.Blocked>(outcome)
    }

    @Test fun `strike with no executor is blocked`() = runBlocking {
        val gate = FakeGate()
        val (exec, _) = executor(gate)
        val outcome = exec.run(strike(), Stock.EMPTY)
        assertIs<StrikeOutcome.Blocked>(outcome)
    }

    @Test fun `policy forbidden blocks before execution`() = runBlocking {
        val registry = DefaultCapabilityRegistry().apply { register(FakeProvider("tool", TOOL, X)) }
        val exec = StrikeExecutor(StrikeResolver(registry), FakeGate(), PolicyEngine(Policy(forbidden = setOf(X))))
        assertIs<StrikeOutcome.Blocked>(exec.run(strike(), Stock.EMPTY))
    }

    @Test fun `policy quota blocks the second execution`() = runBlocking {
        val registry = DefaultCapabilityRegistry().apply { register(FakeProvider("tool", TOOL, X)) }
        val exec = StrikeExecutor(StrikeResolver(registry), FakeGate(), PolicyEngine(Policy(quotas = listOf(Quota(X, max = 1)))))
        assertIs<StrikeOutcome.Done>(exec.run(strike(), Stock.EMPTY))
        assertIs<StrikeOutcome.Blocked>(exec.run(strike(), Stock.EMPTY))
    }
}
