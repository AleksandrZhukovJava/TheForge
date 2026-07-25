package com.forge.workshop.recipe

import com.forge.integration.gitlab.GitLabClient
import com.forge.integration.gitlab.GitLabConfig
import com.forge.integration.gitlab.OpenMergeRequestTool
import com.forge.integration.jira.CreateJiraIssueTool
import com.forge.integration.jira.JiraAuth
import com.forge.integration.jira.JiraClient
import com.forge.integration.jira.JiraConfig
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.secret.SecretStore
import io.ktor.client.HttpClient
import java.net.URI

/**
 * Builds the real Tool for an AUTO node from stored secrets, so a recipe step actually calls the
 * live API (through the engine → policy + Master). Returns null when the integration isn't
 * configured or the capability has no real Tool yet — the runner then falls back to a stub.
 */
object AutoTools {

    suspend fun providerFor(cap: CapabilityId, http: HttpClient, secrets: SecretStore): ExecutorProvider? =
        when (cap.value) {
            "jira.create-issue" -> jira(http, secrets)
            "gitlab.open-merge-request" -> gitlab(http, secrets)
            else -> null
        }

    /** Probe Basic (if email) then Bearer PAT via /myself, return a create-issue Tool on the working auth. */
    private suspend fun jira(http: HttpClient, secrets: SecretStore): ExecutorProvider? {
        val base = secrets.get("jira.base-url")?.trimEnd('/') ?: return null
        val token = secrets.get("jira.token") ?: return null
        val email = secrets.get("jira.email")
        val auths = buildList {
            if (!email.isNullOrBlank()) add(JiraAuth.basic(email, token))
            add(JiraAuth.bearer(token))
        }
        val host = runCatching { URI(base).host }.getOrNull() ?: base
        for (auth in auths) {
            val client = JiraClient(http, JiraConfig(base), auth)
            if (runCatching { client.ping() }.isSuccess) return CreateJiraIssueTool(client, host)
        }
        return null
    }

    private suspend fun gitlab(http: HttpClient, secrets: SecretStore): ExecutorProvider? {
        val base = secrets.get("gitlab.base-url")?.trimEnd('/') ?: return null
        val token = secrets.get("gitlab.token") ?: return null
        val host = runCatching { URI(base).host }.getOrNull() ?: base
        val client = GitLabClient(http, GitLabConfig(base), token)
        return if (runCatching { client.ping() }.isSuccess) OpenMergeRequestTool(client, host) else null
    }
}
