package com.forge.integration.gitlab

import com.forge.brain.resolve.DefaultCapabilityRegistry
import com.forge.brain.resolve.ForbiddenCapabilityException
import com.forge.executors.AbstractTool
import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.domain.StrikeResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitLabIntegrationTest {

    private fun client(body: String): GitLabClient {
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return GitLabClient(HttpClient(engine), GitLabConfig("https://gitlab.example.com"), "tok")
    }

    @Test fun `loadMergeRequest parses the response`() = runBlocking {
        val mr = client("""{"iid":7,"title":"Add engine","state":"opened","web_url":"https://gitlab.example.com/x/-/merge_requests/7"}""")
            .loadMergeRequest("42", 7)
        assertEquals(7, mr.iid)
        assertEquals("opened", mr.state)
    }

    @Test fun `OpenMergeRequestTool is a TOOL and returns the MR`() = runBlocking {
        val tool = OpenMergeRequestTool(
            client("""{"iid":8,"title":"Feature","state":"opened"}"""),
            host = "gitlab.example.com",
        )
        assertEquals(ExecutorKind.TOOL, tool.kind)
        val strike = StrikeDecl(
            StrikeId("s1"), GitLabCapabilities.OPEN_MR,
            input = mapOf("projectId" to "42", "sourceBranch" to "feat", "targetBranch" to "main", "title" to "Feature"),
        )
        assertEquals(8, (tool.execute(strike, Stock.EMPTY).output as MergeRequest).iid)
    }

    @Test fun `provided tools declare no forbidden capability`() {
        val provided = setOf(GitLabCapabilities.LOAD_MR, GitLabCapabilities.OPEN_MR)
        assertTrue(provided.none { it in GitLabCapabilities.FORBIDDEN })
    }

    @Test fun `registry rejects a tool declaring a forbidden gitlab capability`() {
        val registry = DefaultCapabilityRegistry(forbidden = GitLabCapabilities.FORBIDDEN)
        val rogueMergeTool = object : AbstractTool(
            ProviderId("rogue.merge"),
            setOf(Capability(CapabilityId("gitlab.merge"))),
        ) {
            override suspend fun execute(strike: StrikeDecl, stock: Stock) = StrikeResult(strike.id)
        }
        assertFailsWith<ForbiddenCapabilityException> { registry.register(rogueMergeTool) }
    }
}
