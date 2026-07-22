package com.forge.integration.jira

import com.forge.executors.AbstractTool
import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeResult
import com.forge.sdk.security.Permission
import com.forge.sdk.security.RequiresPermissions

object JiraCapabilities {
    val LOAD_ISSUE = CapabilityId("jira.load-issue")
    val CREATE_ISSUE = CapabilityId("jira.create-issue")
}

/** Read a Jira issue. Read-only → Strikes using it are SAFE. */
class LoadJiraIssueTool(
    private val client: JiraClient,
    host: String,
) : AbstractTool(ProviderId("jira.load-issue"), setOf(Capability(JiraCapabilities.LOAD_ISSUE))),
    RequiresPermissions {

    override val requiredPermissions = setOf<Permission>(
        Permission.Network(host), Permission.Secret("jira.token"), Permission.Secret("jira.email"),
    )

    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        val key = strike.input["key"] as? String ?: error("Strike '${strike.id.value}' is missing input 'key'")
        return StrikeResult(strike.id, output = client.loadIssue(key))
    }
}

/** Create a Jira issue. Side-effecting → Strikes using it should be danger = CONFIRM (Master gate). */
class CreateJiraIssueTool(
    private val client: JiraClient,
    host: String,
) : AbstractTool(ProviderId("jira.create-issue"), setOf(Capability(JiraCapabilities.CREATE_ISSUE))),
    RequiresPermissions {

    override val requiredPermissions = setOf<Permission>(
        Permission.Network(host), Permission.Secret("jira.token"), Permission.Secret("jira.email"),
    )

    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        val project = strike.input["project"] as? String ?: error("missing input 'project'")
        val summary = strike.input["summary"] as? String ?: error("missing input 'summary'")
        val issueType = strike.input["issueType"] as? String ?: "Task"
        return StrikeResult(strike.id, output = client.createIssue(project, summary, issueType))
    }
}
