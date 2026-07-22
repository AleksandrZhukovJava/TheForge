package com.forge.brain.resolve

import com.forge.brain.resolve.fakes.FakeProvider
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorKind.TOOL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityRegistryTest {

    private val X = CapabilityId("cap.x")
    private val Y = CapabilityId("cap.y")

    @Test fun `providersFor returns matching providers only`() {
        val reg = DefaultCapabilityRegistry()
        reg.register(FakeProvider("x1", TOOL, X))
        reg.register(FakeProvider("y1", TOOL, Y))
        assertEquals(listOf("x1"), reg.providersFor(X).map { it.id.value })
    }

    @Test fun `capabilities lists every registered id`() {
        val reg = DefaultCapabilityRegistry()
        reg.register(FakeProvider("x1", TOOL, X))
        reg.register(FakeProvider("y1", TOOL, Y))
        assertEquals(setOf(X, Y), reg.capabilities())
    }

    @Test fun `unregister removes the provider`() {
        val reg = DefaultCapabilityRegistry()
        reg.register(FakeProvider("x1", TOOL, X))
        reg.unregister(com.forge.sdk.capability.ProviderId("x1"))
        assertTrue(reg.providersFor(X).isEmpty())
    }

    @Test fun `isForbidden reflects the blacklist`() {
        val reg = DefaultCapabilityRegistry(forbidden = setOf(X))
        assertTrue(reg.isForbidden(X))
        assertFalse(reg.isForbidden(Y))
    }

    @Test fun `registering forbidden capability throws`() {
        val reg = DefaultCapabilityRegistry(forbidden = setOf(X))
        assertFailsWith<ForbiddenCapabilityException> {
            reg.register(FakeProvider("x1", TOOL, X))
        }
    }
}
