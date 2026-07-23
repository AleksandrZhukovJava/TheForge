package com.forge.integration.jira

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

data class JiraConfig(val baseUrl: String)

@Serializable data class JiraIssue(val key: String, val fields: JiraFields)
@Serializable data class JiraFields(val summary: String, val status: JiraStatus)
@Serializable data class JiraStatus(val name: String)
@Serializable data class JiraIssueRef(val key: String)
@Serializable data class JiraSearchResponse(val issues: List<JiraIssue> = emptyList())

@Serializable private data class CreateIssueRequest(val fields: CreateIssueFields)
@Serializable private data class CreateIssueFields(
    val project: ProjectRef,
    val summary: String,
    @SerialName("issuetype") val issueType: IssueTypeRef,
)
@Serializable private data class ProjectRef(val key: String)
@Serializable private data class IssueTypeRef(val name: String)

/** Minimal Jira Cloud REST client (read + create). Deterministic Tool territory — no LLM. */
class JiraClient(
    private val http: HttpClient,
    private val config: JiraConfig,
    private val authHeader: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadIssue(key: String): JiraIssue {
        val body = http.get("${config.baseUrl}/rest/api/3/issue/$key") {
            header(HttpHeaders.Authorization, authHeader)
        }.readJson()
        return json.decodeFromString(body)
    }

    /**
     * Cheap personal poll: issues assigned to the current user, not Done, most-recently updated.
     * Tries REST v3 (Cloud) then falls back to v2 (Server/Data Center).
     */
    suspend fun searchAssignedToMe(maxResults: Int = 20): List<JiraIssue> {
        var last: Exception? = null
        for (version in intArrayOf(3, 2)) {
            try {
                val body = http.get("${config.baseUrl}/rest/api/$version/search") {
                    header(HttpHeaders.Authorization, authHeader)
                    url {
                        parameters.append("jql", "assignee = currentUser() AND statusCategory != Done ORDER BY updated DESC")
                        parameters.append("maxResults", maxResults.toString())
                        parameters.append("fields", "summary,status")
                    }
                }.readJson()
                return json.decodeFromString<JiraSearchResponse>(body).issues
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Jira search failed")
    }

    /** Validates the HTTP response and rejects non-JSON (e.g. an SSO/login HTML page). */
    private suspend fun HttpResponse.readJson(): String {
        val text = bodyAsText()
        if (!status.isSuccess()) {
            throw IllegalStateException("HTTP ${status.value} — проверьте Base URL, email и токен")
        }
        if (text.trimStart().startsWith("<")) {
            throw IllegalStateException("сервер вернул HTML, не JSON — неверный Base URL или требуется вход (SSO)")
        }
        return text
    }

    suspend fun createIssue(project: String, summary: String, issueType: String = "Task"): JiraIssueRef {
        val payload = json.encodeToString(
            CreateIssueRequest(CreateIssueFields(ProjectRef(project), summary, IssueTypeRef(issueType))),
        )
        val body = http.post("${config.baseUrl}/rest/api/3/issue") {
            header(HttpHeaders.Authorization, authHeader)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(payload)
        }.readJson()
        return json.decodeFromString(body)
    }
}

/** Builds the Jira Cloud Basic auth header. Token comes from the SecretStore, never from code. */
object JiraAuth {
    fun basic(email: String, apiToken: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$email:$apiToken".toByteArray())
}
