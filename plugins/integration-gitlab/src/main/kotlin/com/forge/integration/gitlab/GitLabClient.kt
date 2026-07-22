package com.forge.integration.gitlab

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
        }.bodyAsText()
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
        }.bodyAsText()
        return json.decodeFromString(body)
    }
}
