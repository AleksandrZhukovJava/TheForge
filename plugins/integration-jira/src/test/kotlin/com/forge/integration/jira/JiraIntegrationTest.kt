package com.forge.integration.jira

import com.forge.executors.secret.InMemorySecretStore
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.context.Stock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JiraIntegrationTest {

    private fun client(body: String): JiraClient {
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return JiraClient(HttpClient(engine), JiraConfig("https://acme.atlassian.net"), "Basic xxx")
    }

    @Test fun `loadIssue parses the response`() = runBlocking {
        val issue = client("""{"key":"FORGE-1","fields":{"summary":"Bootstrap","status":{"name":"To Do"}}}""")
            .loadIssue("FORGE-1")
        assertEquals("FORGE-1", issue.key)
        assertEquals("Bootstrap", issue.fields.summary)
        assertEquals("To Do", issue.fields.status.name)
    }

    @Test fun `createIssue parses the reference`() = runBlocking {
        val ref = client("""{"key":"FORGE-2","id":"10002","self":"https://acme.atlassian.net/rest/api/3/issue/10002"}""")
            .createIssue("FORGE", "New task")
        assertEquals("FORGE-2", ref.key)
    }

    @Test fun `searchAssignedToMe parses the issue list`() = runBlocking {
        val issues = client(
            """{"issues":[
                {"key":"FORGE-1","fields":{"summary":"A","status":{"name":"In Progress"}}},
                {"key":"FORGE-2","fields":{"summary":"B","status":{"name":"To Do"}}}
            ]}""",
        ).searchAssignedToMe()
        assertEquals(2, issues.size)
        assertEquals("FORGE-1", issues.first().key)
        assertEquals("To Do", issues[1].fields.status.name)
    }

    @Test fun `LoadJiraIssueTool is a TOOL and returns the issue`() = runBlocking {
        val tool = LoadJiraIssueTool(
            client("""{"key":"FORGE-1","fields":{"summary":"S","status":{"name":"Done"}}}"""),
            host = "acme.atlassian.net",
        )
        assertEquals(ExecutorKind.TOOL, tool.kind)
        val result = tool.execute(StrikeDecl(StrikeId("s1"), JiraCapabilities.LOAD_ISSUE, input = mapOf("key" to "FORGE-1")), Stock.EMPTY)
        assertEquals("FORGE-1", (result.output as JiraIssue).key)
    }

    @Test fun `auth header is built from secrets, not code`() = runBlocking {
        val secrets = InMemorySecretStore(mapOf("jira.email" to "me@acme.io", "jira.token" to "t0ken"))
        val header = JiraAuth.basic(secrets.require("jira.email"), secrets.require("jira.token"))
        assertTrue(header.startsWith("Basic "))
    }
}
