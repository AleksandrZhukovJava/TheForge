package com.forge.brain.resolve

import com.forge.brain.resolve.fakes.FakeProvider
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.capability.ExecutorKind.MASTER
import com.forge.sdk.capability.ExecutorKind.PRESS
import com.forge.sdk.capability.ExecutorKind.SMITH
import com.forge.sdk.capability.ExecutorKind.TOOL
import com.forge.sdk.common.CaretRange
import com.forge.sdk.common.SemVer
import com.forge.sdk.common.VersionConstraint
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Covers the 15-case selection matrix from the plan. */
class StrikeResolverTest {

    private val X = CapabilityId("cap.x")

    private fun registry(forbidden: Set<CapabilityId> = emptySet()) =
        DefaultCapabilityRegistry(forbidden)

    private fun resolver(
        registry: CapabilityRegistry,
        onAmbiguity: (AmbiguousProviderEvent) -> Unit = {},
    ) = StrikeResolver(registry, onAmbiguity)

    private fun strike(
        danger: DangerLevel = DangerLevel.SAFE,
        constraint: VersionConstraint = com.forge.sdk.common.AnyVersion,
    ) = StrikeDecl(StrikeId("s1"), X, danger, constraint)

    private fun selected(r: Resolution): Resolution.Selected {
        assertIs<Resolution.Selected>(r)
        return r
    }

    // 1
    @Test fun `single tool is selected`() {
        val reg = registry().apply { register(FakeProvider("tool", TOOL, X)) }
        val r = selected(resolver(reg).resolve(strike(), Stock.EMPTY))
        assertEquals("tool", r.provider.id.value)
        assertTrue(!r.requiresConfirmation)
    }

    // 2
    @Test fun `falls through to smith when tool cannot handle`() {
        val reg = registry().apply {
            register(FakeProvider("tool", TOOL, X, canHandleFn = { _, _ -> false }))
            register(FakeProvider("smith", SMITH, X))
        }
        assertEquals("smith", selected(resolver(reg).resolve(strike(), Stock.EMPTY)).provider.id.value)
    }

    // 3
    @Test fun `tool wins over press and smith by kind`() {
        val reg = registry().apply {
            register(FakeProvider("smith", SMITH, X))
            register(FakeProvider("press", PRESS, X))
            register(FakeProvider("tool", TOOL, X))
        }
        assertEquals("tool", selected(resolver(reg).resolve(strike(), Stock.EMPTY)).provider.id.value)
    }

    // 4
    @Test fun `press wins over master and smith`() {
        val reg = registry().apply {
            register(FakeProvider("smith", SMITH, X))
            register(FakeProvider("master", MASTER, X))
            register(FakeProvider("press", PRESS, X))
        }
        assertEquals("press", selected(resolver(reg).resolve(strike(), Stock.EMPTY)).provider.id.value)
    }

    // 5
    @Test fun `no executor when all decline`() {
        val reg = registry().apply {
            register(FakeProvider("tool", TOOL, X, canHandleFn = { _, _ -> false }))
            register(FakeProvider("press", PRESS, X, canHandleFn = { _, _ -> false }))
            register(FakeProvider("master", MASTER, X, canHandleFn = { _, _ -> false }))
            register(FakeProvider("smith", SMITH, X, canHandleFn = { _, _ -> false }))
        }
        assertIs<Resolution.NoExecutor>(resolver(reg).resolve(strike(), Stock.EMPTY))
    }

    // 6
    @Test fun `no executor when none registered`() {
        assertIs<Resolution.NoExecutor>(resolver(registry()).resolve(strike(), Stock.EMPTY))
    }

    // 7
    @Test fun `higher priority wins within same kind`() {
        val reg = registry().apply {
            register(FakeProvider("low", TOOL, X, priority = 0))
            register(FakeProvider("high", TOOL, X, priority = 10))
        }
        assertEquals("high", selected(resolver(reg).resolve(strike(), Stock.EMPTY)).provider.id.value)
    }

    // 8
    @Test fun `equal candidates tie-break by id and emit ambiguity`() {
        val reg = registry().apply {
            register(FakeProvider("b", TOOL, X, priority = 0))
            register(FakeProvider("a", TOOL, X, priority = 0))
        }
        val events = mutableListOf<AmbiguousProviderEvent>()
        val r = selected(resolver(reg) { events += it }.resolve(strike(), Stock.EMPTY))
        assertEquals("a", r.provider.id.value)
        assertEquals(1, events.size)
        assertEquals(listOf("a", "b"), events.first().contenders.map { it.value })
        assertEquals("a", events.first().chosen.value)
    }

    // 9
    @Test fun `forbidden capability resolves to Forbidden even with a provider`() {
        val reg = registry(forbidden = setOf(X))
        // provider for a different capability so registration itself does not throw
        reg.register(FakeProvider("tool", TOOL, CapabilityId("cap.other")))
        assertIs<Resolution.Forbidden>(resolver(reg).resolve(strike(), Stock.EMPTY))
    }

    // 10
    @Test fun `registering a forbidden capability throws`() {
        val reg = registry(forbidden = setOf(X))
        assertFailsWith<ForbiddenCapabilityException> {
            reg.register(FakeProvider("tool", TOOL, X))
        }
    }

    // 11
    @Test fun `confirm danger requires confirmation`() {
        val reg = registry().apply { register(FakeProvider("tool", TOOL, X)) }
        val r = selected(resolver(reg).resolve(strike(danger = DangerLevel.CONFIRM), Stock.EMPTY))
        assertTrue(r.requiresConfirmation)
    }

    // 12
    @Test fun `forbidden danger short-circuits`() {
        val reg = registry().apply { register(FakeProvider("tool", TOOL, X)) }
        assertIs<Resolution.Forbidden>(
            resolver(reg).resolve(strike(danger = DangerLevel.FORBIDDEN), Stock.EMPTY),
        )
    }

    // 13
    @Test fun `selection depends on stock via canHandle`() {
        val reg = registry().apply {
            register(FakeProvider("smith", SMITH, X, canHandleFn = { _, stock -> stock.has("ready") }))
        }
        assertIs<Resolution.NoExecutor>(resolver(reg).resolve(strike(), Stock.EMPTY))
        val ready = Stock(mapOf("ready" to true))
        assertEquals("smith", selected(resolver(reg).resolve(strike(), ready)).provider.id.value)
    }

    // 14
    @Test fun `re-registering same provider id keeps one instance`() {
        val reg = registry().apply {
            register(FakeProvider("tool", TOOL, X, priority = 0))
            register(FakeProvider("tool", TOOL, X, priority = 5))
        }
        assertEquals(1, reg.providersFor(X).size)
        assertEquals(5, reg.providersFor(X).first().priority)
    }

    // 15
    @Test fun `version constraint filters providers`() {
        val reg = registry().apply {
            register(FakeProvider("v1", TOOL, X, version = SemVer(1, 0, 0)))
            register(FakeProvider("v2", TOOL, X, version = SemVer(2, 0, 0)))
        }
        val r = selected(resolver(reg).resolve(strike(constraint = CaretRange(SemVer(2, 0, 0))), Stock.EMPTY))
        assertEquals("v2", r.provider.id.value)
    }

    @Test fun `resolution is deterministic across repeated runs`() {
        val reg = registry().apply {
            register(FakeProvider("b", TOOL, X))
            register(FakeProvider("a", TOOL, X))
            register(FakeProvider("smith", SMITH, X))
        }
        val first = selected(resolver(reg).resolve(strike(), Stock.EMPTY)).provider.id.value
        repeat(20) {
            assertEquals(first, selected(resolver(reg).resolve(strike(), Stock.EMPTY)).provider.id.value)
        }
    }
}
