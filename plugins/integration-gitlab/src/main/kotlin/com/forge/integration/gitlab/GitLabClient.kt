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
data class GitLabProject(
    val id: Int,
    @SerialName("path_with_namespace") val path: String = "",
    val name: String = "",
)

@Serializable
data class GitLabBranch(
    val name: String,
    val default: Boolean = false,
)

@Serializable
private data class OpenMrRequest(
    @SerialName("source_branch") val sourceBranch: String,
    @SerialName("target_branch") val targetBranch: String,
    val title: String,
    val description: String? = null,
    @SerialName("remove_source_branch") val removeSourceBranch: Boolean = false,
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

    /** Cheap auth probe (`/user`). Throws if the token is not accepted. */
    suspend fun ping() {
        http.get("${config.baseUrl}/api/v4/user") {
            header("PRIVATE-TOKEN", token)
            header(HttpHeaders.Accept, "application/json")
        }.readJson()
    }

    /** Projects the current user is a member of (for the create-MR picker), most-recently active first. */
    suspend fun getProjects(perPage: Int = 50): List<GitLabProject> {
        val body = http.get("${config.baseUrl}/api/v4/projects") {
            header("PRIVATE-TOKEN", token)
            header(HttpHeaders.Accept, "application/json")
            url {
                parameters.append("membership", "true")
                parameters.append("order_by", "last_activity_at")
                parameters.append("simple", "true")
                parameters.append("per_page", perPage.toString())
            }
        }.readJson()
        return json.decodeFromString<List<GitLabProject>>(body).sortedBy { it.path }
    }

    /** Branches in a project (source/target pickers). Default branch surfaces first. */
    suspend fun getBranches(projectId: String, perPage: Int = 100): List<GitLabBranch> {
        val body = http.get("${config.baseUrl}/api/v4/projects/$projectId/repository/branches") {
            header("PRIVATE-TOKEN", token)
            header(HttpHeaders.Accept, "application/json")
            url { parameters.append("per_page", perPage.toString()) }
        }.readJson()
        return json.decodeFromString<List<GitLabBranch>>(body)
            .sortedWith(compareByDescending<GitLabBranch> { it.default }.thenBy { it.name })
    }

    suspend fun openMergeRequest(
        projectId: String,
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String? = null,
        removeSourceBranch: Boolean = false,
    ): MergeRequest {
        val body = http.post("${config.baseUrl}/api/v4/projects/$projectId/merge_requests") {
            header("PRIVATE-TOKEN", token)
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(OpenMrRequest(sourceBranch, targetBranch, title, description?.takeIf { it.isNotBlank() }, removeSourceBranch)))
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
