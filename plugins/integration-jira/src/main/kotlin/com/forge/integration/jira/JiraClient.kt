package com.forge.integration.jira

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

data class JiraConfig(val baseUrl: String)

@Serializable data class JiraIssue(val key: String, val fields: JiraFields)
@Serializable data class JiraFields(val summary: String, val status: JiraStatus)
@Serializable data class JiraStatus(val name: String)
@Serializable data class JiraIssueRef(val key: String)
@Serializable data class JiraSearchResponse(val issues: List<JiraIssue> = emptyList())
@Serializable private data class JiraSearchRequest(val jql: String, val maxResults: Int, val fields: List<String>)
@Serializable data class JiraProject(val key: String, val name: String = "")
@Serializable data class JiraIssueType(val id: String = "", val name: String)
@Serializable private data class CreateMetaTypes(val values: List<JiraIssueType> = emptyList(), val issueTypes: List<JiraIssueType> = emptyList())


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
            header(HttpHeaders.Accept, "application/json")
        }.readJson()
        return json.decodeFromString(body)
    }

    /**
     * Cheap personal poll: issues assigned to the current user, not Done, most-recently updated.
     * POST search — Cloud `/rest/api/3/search/jql`, then Server/DC `/rest/api/2/search` (as the
     * working widget does). `Accept: application/json` prevents an HTML login page being returned.
     */
    suspend fun searchAssignedToMe(maxResults: Int = 20): List<JiraIssue> {
        val payload = json.encodeToString(
            JiraSearchRequest(
                jql = "assignee = currentUser() AND statusCategory != Done ORDER BY updated DESC",
                maxResults = maxResults,
                fields = listOf("summary", "status"),
            ),
        )
        var last: Exception? = null
        for (path in listOf("/rest/api/3/search/jql", "/rest/api/2/search")) {
            try {
                val body = http.post("${config.baseUrl}$path") {
                    header(HttpHeaders.Authorization, authHeader)
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(payload)
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

    /** Cheap auth probe (`/myself`). Throws if the current auth header is not accepted. */
    suspend fun ping() {
        var last: Exception? = null
        for (version in intArrayOf(3, 2)) {
            try {
                http.get("${config.baseUrl}/rest/api/$version/myself") {
                    header(HttpHeaders.Authorization, authHeader)
                    header(HttpHeaders.Accept, "application/json")
                }.readJson()
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Jira auth failed")
    }

    /** Projects visible to the current user (for the create-form picker). Cloud v3 then Server/DC v2. */
    suspend fun getProjects(): List<JiraProject> {
        var last: Exception? = null
        for (version in intArrayOf(3, 2)) {
            try {
                val body = http.get("${config.baseUrl}/rest/api/$version/project") {
                    header(HttpHeaders.Authorization, authHeader)
                    header(HttpHeaders.Accept, "application/json")
                }.readJson()
                return json.decodeFromString<List<JiraProject>>(body).sortedBy { it.key }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Jira projects failed")
    }

    /**
     * Issue types selectable when creating in a project, via granular createmeta
     * (`/issue/createmeta/{key}/issuetypes` — the one path that exists on both Cloud and Server 8.4+).
     */
    suspend fun getIssueTypes(projectKey: String): List<JiraIssueType> {
        var last: Exception? = null
        for (version in intArrayOf(3, 2)) {
            try {
                val body = http.get("${config.baseUrl}/rest/api/$version/issue/createmeta/$projectKey/issuetypes") {
                    header(HttpHeaders.Authorization, authHeader)
                    header(HttpHeaders.Accept, "application/json")
                }.readJson()
                val meta = json.decodeFromString<CreateMetaTypes>(body)
                return meta.values.ifEmpty { meta.issueTypes }.filter { it.name.isNotBlank() }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Jira issue types failed")
    }

    suspend fun createIssue(project: String, summary: String, issueType: String = "Task", description: String? = null): JiraIssueRef {
        // Try Cloud v3 (description = ADF), then Server/DC v2 (description = plain text). A failing
        // attempt created nothing (bad endpoint/auth/shape), so retrying the other version is safe.
        var last: Exception? = null
        for (version in intArrayOf(3, 2)) {
            try {
                val payload = buildJsonObject {
                    putJsonObject("fields") {
                        putJsonObject("project") { put("key", project) }
                        put("summary", summary)
                        putJsonObject("issuetype") { put("name", issueType) }
                        if (!description.isNullOrBlank()) {
                            if (version == 3) put("description", textToAdf(description)) else put("description", description)
                        }
                    }
                }.toString()
                val body = http.post("${config.baseUrl}/rest/api/$version/issue") {
                    header(HttpHeaders.Authorization, authHeader)
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(payload)
                }.readJson()
                return json.decodeFromString(body)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Jira create failed")
    }

    /** Plain text → Atlassian Document Format (required for Cloud v3 description). */
    private fun textToAdf(text: String): JsonObject = buildJsonObject {
        put("type", "doc")
        put("version", 1)
        putJsonArray("content") {
            text.split("\n").forEach { line ->
                addJsonObject {
                    put("type", "paragraph")
                    if (line.isNotEmpty()) {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", line)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Builds the Jira Cloud Basic auth header. Token comes from the SecretStore, never from code. */
object JiraAuth {
    /** Cloud: email + API token. */
    fun basic(email: String, apiToken: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$email:$apiToken".toByteArray())

    /** Server / Data Center: Personal Access Token. */
    fun bearer(pat: String): String = "Bearer $pat"
}
