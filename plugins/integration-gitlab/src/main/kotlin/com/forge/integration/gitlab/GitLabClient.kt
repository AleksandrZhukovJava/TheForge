package com.forge.integration.gitlab

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

data class GitLabConfig(val baseUrl: String)

@Serializable
data class MergeRequest(
    val iid: Int,
    val title: String,
    val state: String,
    @SerialName("web_url") val webUrl: String? = null,
)

@Serializable
private data class OpenMrRequest(
    @SerialName("source_branch") val sourceBranch: String,
    @SerialName("target_branch") val targetBranch: String,
    val title: String,
)

/**
 * Minimal GitLab REST client.
 *
 * DELIBERATELY has no `merge()` and no `forcePush()`: those are FORBIDDEN by project policy
 * ([GitLabCapabilities.FORBIDDEN]). The capability simply does not exist here, and Forge Core
 * blacklists it too — so it cannot be reached even by a rogue plugin.
 */
class GitLabClient(
    private val http: HttpClient,
    private val config: GitLabConfig,
    private val token: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadMergeRequest(projectId: String, iid: Int): MergeRequest {
        val body = http.get("${config.baseUrl}/api/v4/projects/$projectId/merge_requests/$iid") {
            header("PRIVATE-TOKEN", token)
        }.readJson()
        return json.decodeFromString(body)
    }

    /** Cheap personal poll: open merge requests assigned to the current user. */
    suspend fun listAssignedMergeRequests(perPage: Int = 20): List<MergeRequest> {
        val body = http.get("${config.baseUrl}/api/v4/merge_requests") {
            header("PRIVATE-TOKEN", token)
            header(HttpHeaders.Accept, "application/json")
            url {
                parameters.append("scope", "assigned_to_me")
                parameters.append("state", "opened")
                parameters.append("per_page", perPage.toString())
            }
        }.readJson()
        return json.decodeFromString(body)
    }

    suspend fun openMergeRequest(
        projectId: String,
        sourceBranch: String,
        targetBranch: String,
        title: String,
    ): MergeRequest {
        val body = http.post("${config.baseUrl}/api/v4/projects/$projectId/merge_requests") {
            header("PRIVATE-TOKEN", token)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(OpenMrRequest(sourceBranch, targetBranch, title)))
        }.readJson()
        return json.decodeFromString(body)
    }

    /** Validates the HTTP response and rejects non-JSON (e.g. an SSO/login HTML page). */
    private suspend fun HttpResponse.readJson(): String {
        val text = bodyAsText()
        if (!status.isSuccess()) {
            throw IllegalStateException("HTTP ${status.value} — проверьте Base URL и токен")
        }
        if (text.trimStart().startsWith("<")) {
            throw IllegalStateException("сервер вернул HTML, не JSON — неверный Base URL или требуется вход (SSO)")
        }
        return text
    }
}
