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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Live data via the cheap personal endpoints (Jira `assignee=currentUser()`, GitLab
 * `assigned_to_me`). Each integration is optional: whichever has a token contributes its rows.
 * Throws [NotConfiguredException] when neither is set up.
 *
 * Fast path: one shared HTTP client, Jira and GitLab fetched in parallel, and the working Jira auth
 * scheme is cached so we stop re-probing Basic-then-Bearer on every poll (the main startup lag).
 */
class LiveDashboardRepository(private val secrets: SecretStore) : DashboardRepository {

    private val http = HttpClient(CIO)
    @Volatile private var workingJiraAuth: String? = null
    @Volatile private var gitlabUserId: Int? = null

    override suspend fun load(): DashboardData = coroutineScope {
        val jiraD = async { named("Jira") { loadJira() } }
        val mrsD = async { named("GitLab") { loadMergeRequests() } }
        val jira = jiraD.await()
        val mrs = mrsD.await()
        if (jira == null && mrs == null) throw NotConfiguredException()
        DashboardData(
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
        val base = secrets.get("jira.base-url")?.trimEnd('/') ?: return null
        val token = secrets.get("jira.token") ?: return null
        val email = secrets.get("jira.email")
        // Email present → Cloud Basic; also try Bearer PAT (Server/Data Center).
        val allAuths = buildList {
            if (!email.isNullOrBlank()) add(JiraAuth.basic(email, token))
            add(JiraAuth.bearer(token))
        }
        // Try the last-known-good scheme first so we don't re-probe failing ones every poll.
        val ordered = (listOfNotNull(workingJiraAuth) + allAuths).distinct().filter { it in allAuths }
        var last: Exception? = null
        for (auth in ordered) {
            try {
                val rows = JiraClient(http, JiraConfig(base), auth).searchAssignedToMe()
                    .map { WRow(it.key, it.fields.summary, mapJiraStatus(it.fields.status.name), url = "$base/browse/${it.key}", statusName = it.fields.status.name) }
                workingJiraAuth = auth
                return rows
            } catch (e: Exception) {
                last = e
            }
        }
        // Every scheme failed — the cached one may be stale, so clear it for a fresh probe next time.
        workingJiraAuth = null
        throw (last ?: IllegalStateException("не удалось получить задачи"))
    }

    private suspend fun loadMergeRequests(): List<WRow>? {
        val url = secrets.get("gitlab.base-url")?.trimEnd('/') ?: return null
        val token = secrets.get("gitlab.token") ?: return null
        val client = GitLabClient(http, GitLabConfig(url), token)
        // MRs where I'm the reviewer ("на моём ревью"); the user id is cached across polls.
        val uid = gitlabUserId ?: client.currentUser().id.also { gitlabUserId = it }
        return client.listReviewMergeRequests(uid)
            .map { WRow("!${it.iid}", it.title, mapMrState(it.state), url = it.webUrl, statusName = it.state) }
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
