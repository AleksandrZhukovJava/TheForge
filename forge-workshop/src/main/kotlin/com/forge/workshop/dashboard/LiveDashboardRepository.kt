package com.forge.workshop.dashboard

import com.forge.integration.gitlab.GitLabClient
import com.forge.integration.gitlab.GitLabConfig
import com.forge.integration.jira.JiraAuth
import com.forge.integration.jira.JiraClient
import com.forge.integration.jira.JiraConfig
import com.forge.sdk.secret.SecretStore
import com.forge.workshop.ui.PillStatus
import com.forge.workshop.widget.WRow
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Live data via the cheap personal endpoints (Jira `assignee=currentUser()`, GitLab
 * `assigned_to_me`). Each integration is optional: whichever has a token contributes its rows.
 * Throws [NotConfiguredException] when neither is set up.
 */
class LiveDashboardRepository(private val secrets: SecretStore) : DashboardRepository {

    override suspend fun load(): DashboardData {
        val jira = named("Jira") { loadJira() }
        val mrs = named("GitLab") { loadMergeRequests() }
        if (jira == null && mrs == null) throw NotConfiguredException()
        return DashboardData(
            jira = jira ?: emptyList(),
            mrs = mrs ?: emptyList(),
            pipelines = emptyList(),
        )
    }

    /** Prefix any failure with the integration name so the widget shows which one is misconfigured. */
    private suspend fun <T> named(name: String, block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: NotConfiguredException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("$name: ${e.message}", e)
        }

    private suspend fun loadJira(): List<WRow>? {
        val url = secrets.get("jira.base-url")?.trimEnd('/') ?: return null
        val email = secrets.get("jira.email") ?: return null
        val token = secrets.get("jira.token") ?: return null
        val http = HttpClient(CIO)
        return try {
            JiraClient(http, JiraConfig(url), JiraAuth.basic(email, token))
                .searchAssignedToMe()
                .map { WRow(it.key, it.fields.summary, mapJiraStatus(it.fields.status.name)) }
        } finally {
            http.close()
        }
    }

    private suspend fun loadMergeRequests(): List<WRow>? {
        val url = secrets.get("gitlab.base-url")?.trimEnd('/') ?: return null
        val token = secrets.get("gitlab.token") ?: return null
        val http = HttpClient(CIO)
        return try {
            GitLabClient(http, GitLabConfig(url), token)
                .listAssignedMergeRequests()
                .map { WRow("!${it.iid}", it.title, mapMrState(it.state)) }
        } finally {
            http.close()
        }
    }

    private fun mapJiraStatus(name: String): PillStatus = when (name.lowercase()) {
        "done", "closed", "resolved" -> PillStatus.DONE
        "to do", "open", "backlog", "to-do" -> PillStatus.TODO
        else -> PillStatus.IN_PROGRESS
    }

    private fun mapMrState(state: String): PillStatus = when (state.lowercase()) {
        "merged" -> PillStatus.MERGED
        "opened" -> PillStatus.OPENED
        else -> PillStatus.DRAFT
    }
}
