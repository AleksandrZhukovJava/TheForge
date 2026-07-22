package com.forge.executors

import com.forge.executors.gates.AutoApproveGate
import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.master.DecideRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ExecutorsTest {

    private val X = CapabilityId("cap.x")
    private fun strike() = StrikeDecl(StrikeId("s1"), X)

    @Test fun `AbstractTool is a TOOL and executes`() = runBlocking {
        val tool = object : AbstractTool(ProviderId("t"), setOf(Capability(X))) {
            override suspend fun execute(strike: StrikeDecl, stock: Stock) =
                com.forge.sdk.domain.StrikeResult(strike.id, output = "ok")
        }
        assertEquals(ExecutorKind.TOOL, tool.kind)
        assertEquals("ok", tool.execute(strike(), Stock.EMPTY).output)
    }

    @Test fun `MasterExecutor returns the human decision`() = runBlocking {
        val master = MasterExecutor(
            id = ProviderId("m"),
            capabilities = setOf(Capability(X)),
            gate = AutoApproveGate,
            promptFor = { DecideRequest(it.id, it.capability, "Choose", options = listOf("main", "dev")) },
        )
        assertEquals(ExecutorKind.MASTER, master.kind)
        assertEquals("main", master.execute(strike(), Stock.EMPTY).output)
    }
}
