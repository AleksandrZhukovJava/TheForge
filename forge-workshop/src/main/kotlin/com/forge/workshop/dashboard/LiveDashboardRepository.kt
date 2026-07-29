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
    // Last good rows per section, so one integration failing (e.g. GitLab while on VPN) doesn't
    // freeze the other — Jira statuses keep updating even if GitLab is down, and vice versa.
    @Volatile private var lastJira: List<WRow>? = null
    @Volatile private var lastMrs: List<WRow>? = null

    override suspend fun load(): DashboardData = coroutineScope {
        val jiraR = async { runCatching { loadJira() } }
        val mrsR = async { runCatching { loadMergeRequests() } }
        val jr = jiraR.await()
        val mr = mrsR.await()

        // Success (incl. null = not configured) refreshes the cache; a failure keeps the last good.
        val jira = if (jr.isSuccess) jr.getOrNull().also { lastJira = it } else lastJira
        val mrs = if (mr.isSuccess) mr.getOrNull().also { lastMrs = it } else lastMrs

        when {
            jira != null || mrs != null -> DashboardData(jira ?: emptyList(), mrs ?: emptyList(), emptyList())
            jr.isSuccess && mr.isSuccess -> throw NotConfiguredException()
            else -> throw (jr.exceptionOrNull() ?: mr.exceptionOrNull() ?: NotConfiguredException())
        }
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
