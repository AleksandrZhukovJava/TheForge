package com.forge.brain.recipe

import com.forge.brain.execute.StrikeExecutor
import com.forge.brain.execute.StrikeOutcome
import com.forge.brain.policy.PolicyEngine
import com.forge.brain.resolve.DefaultCapabilityRegistry
import com.forge.brain.resolve.StrikeResolver
import com.forge.brain.resolve.fakes.FakeGate
import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.EdgeCondition
import com.forge.sdk.domain.Recipe
import com.forge.sdk.domain.RecipeEdge
import com.forge.sdk.domain.RecipeId
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.domain.StrikeResult
import com.forge.sdk.domain.StrikeStatus
import com.forge.sdk.policy.Policy
import com.forge.sdk.policy.Quota
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Test provider: returns a fixed output/status and can capture the Stock it received. */
private class EchoProvider(
    id: String,
    capability: CapabilityId,
    private val out: Any? = "$id:out",
    private val status: StrikeStatus = StrikeStatus.OK,
    private val sink: ((Stock) -> Unit)? = null,
) : ExecutorProvider {
    override val id = ProviderId(id)
    override val kind = ExecutorKind.TOOL
    override val capabilities = setOf(Capability(capability))
    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        sink?.invoke(stock)
        return StrikeResult(strike.id, output = out, status = status)
    }
}

class RecipeEngineTest {

    private val A = CapabilityId("cap.a")
    private val B = CapabilityId("cap.b")
    private val C = CapabilityId("cap.c")

    private fun executor(gate: FakeGate = FakeGate(), policy: PolicyEngine = PolicyEngine(Policy()), vararg providers: ExecutorProvider): StrikeExecutor {
        val registry = DefaultCapabilityRegistry()
        providers.forEach { registry.register(it) }
        return StrikeExecutor(StrikeResolver(registry), gate, policy)
    }

    @Test fun `linear recipe runs in order and passes outputs downstream`() = runBlocking {
        var seenByB: Stock? = null
        val exec = executor(
            providers = arrayOf(
                EchoProvider("a", A, out = "A-RESULT"),
                EchoProvider("b", B, sink = { seenByB = it }),
            ),
        )
        val s1 = StrikeDecl(StrikeId("s1"), A)
        val s2 = StrikeDecl(StrikeId("s2"), B)
        val run = RecipeEngine(exec).run(Recipe(RecipeId("r"), listOf(s1, s2)))

        assertEquals(RecipeStatus.COMPLETED, run.status)
        assertEquals(listOf("s1", "s2"), run.steps.map { it.strikeId.value })
        assertEquals("A-RESULT", seenByB!!["s1"])
    }

    @Test fun `confirm strike asks the Master`() = runBlocking {
        val gate = FakeGate(approve = true)
        val exec = executor(gate = gate, providers = arrayOf(EchoProvider("a", A)))
        val s1 = StrikeDecl(StrikeId("s1"), A, DangerLevel.CONFIRM)
        RecipeEngine(exec).run(Recipe(RecipeId("r"), listOf(s1)))
        assertEquals(1, gate.confirms.size)
    }

    @Test fun `quota blocks the second create and halts the recipe`() = runBlocking {
        val CREATE = CapabilityId("jira.create-issue")
        val exec = executor(
            policy = PolicyEngine(Policy(quotas = listOf(Quota(CREATE, max = 1)))),
            providers = arrayOf(EchoProvider("jira", CREATE)),
        )
        val s1 = StrikeDecl(StrikeId("s1"), CREATE)
        val s2 = StrikeDecl(StrikeId("s2"), CREATE)
        val run = RecipeEngine(exec).run(Recipe(RecipeId("r"), listOf(s1, s2)))

        assertEquals(RecipeStatus.HALTED, run.status)
        assertIs<StrikeOutcome.Done>(run.steps[0].outcome)
        assertIs<StrikeOutcome.Blocked>(run.steps[1].outcome)
    }

    @Test fun `fork is resolved by the branch chooser`() = runBlocking {
        val exec = executor(
            providers = arrayOf(EchoProvider("a", A), EchoProvider("b", B), EchoProvider("c", C)),
        )
        val s1 = StrikeDecl(StrikeId("s1"), A)
        val s2 = StrikeDecl(StrikeId("s2"), B)
        val s3 = StrikeDecl(StrikeId("s3"), C)
        val recipe = Recipe(
            RecipeId("r"),
            strikes = listOf(s1, s2, s3),
            edges = listOf(RecipeEdge(s1.id, s2.id), RecipeEdge(s1.id, s3.id)),
        )
        val chooser = BranchChooser { _, options -> options.first { it.value == "s3" } }
        val run = RecipeEngine(exec, chooser).run(recipe)

        assertEquals(listOf("s1", "s3"), run.steps.map { it.strikeId.value })
    }

    @Test fun `edge conditions route on success and failure`() = runBlocking {
        val exec = executor(
            providers = arrayOf(
                EchoProvider("a", A, status = StrikeStatus.FAILED),
                EchoProvider("b", B),
                EchoProvider("c", C),
            ),
        )
        val s1 = StrikeDecl(StrikeId("s1"), A)
        val s2 = StrikeDecl(StrikeId("s2"), B)
        val s3 = StrikeDecl(StrikeId("s3"), C)
        val recipe = Recipe(
            RecipeId("r"),
            strikes = listOf(s1, s2, s3),
            edges = listOf(
                RecipeEdge(s1.id, s2.id, EdgeCondition.ON_SUCCESS),
                RecipeEdge(s1.id, s3.id, EdgeCondition.ON_FAILURE),
            ),
        )
        val run = RecipeEngine(exec).run(recipe)
        assertEquals(listOf("s1", "s3"), run.steps.map { it.strikeId.value })
    }

    @Test fun `missing executor halts the recipe`() = runBlocking {
        val exec = executor(providers = arrayOf(EchoProvider("a", A)))
        val s1 = StrikeDecl(StrikeId("s1"), CapabilityId("cap.missing"))
        val run = RecipeEngine(exec).run(Recipe(RecipeId("r"), listOf(s1)))

        assertEquals(RecipeStatus.HALTED, run.status)
        assertIs<StrikeOutcome.Blocked>(run.steps[0].outcome)
        assertNull(run.steps.getOrNull(1))
    }
}
