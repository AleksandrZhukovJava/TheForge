package com.forge.integration.gitlab

import com.forge.executors.AbstractTool
import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeResult
import com.forge.sdk.security.Permission
import com.forge.sdk.security.RequiresPermissions

object GitLabCapabilities {
    val LOAD_MR = CapabilityId("gitlab.load-merge-request")
    val OPEN_MR = CapabilityId("gitlab.open-merge-request")

    /**
     * Hard-banned capabilities. Not implemented by any Tool here, and meant to seed Forge Core's
     * registry blacklist so they can never be registered or resolved.
     */
    val FORBIDDEN = setOf(
        CapabilityId("gitlab.merge"),
        CapabilityId("gitlab.force-push"),
    )
}

/** Read a merge request. Read-only → SAFE. */
class LoadMergeRequestTool(
    private val client: GitLabClient,
    host: String,
) : AbstractTool(ProviderId("gitlab.load-merge-request"), setOf(Capability(GitLabCapabilities.LOAD_MR))),
    RequiresPermissions {

    override val requiredPermissions = setOf<Permission>(
        Permission.Network(host), Permission.Secret("gitlab.token"),
    )

    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        val projectId = strike.input["projectId"] as? String ?: error("missing input 'projectId'")
        val iid = (strike.input["iid"] as? Number)?.toInt() ?: error("missing input 'iid'")
        return StrikeResult(strike.id, output = client.loadMergeRequest(projectId, iid))
    }
}

/** Open a merge request. Side-effecting → Strikes should be danger = CONFIRM. Merge stays FORBIDDEN. */
class OpenMergeRequestTool(
    private val client: GitLabClient,
    host: String,
) : AbstractTool(ProviderId("gitlab.open-merge-request"), setOf(Capability(GitLabCapabilities.OPEN_MR))),
    RequiresPermissions {

    override val requiredPermissions = setOf<Permission>(
        Permission.Network(host), Permission.Secret("gitlab.token"),
    )

    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        val projectId = strike.input["projectId"] as? String ?: error("missing input 'projectId'")
        val source = strike.input["sourceBranch"] as? String ?: error("missing input 'sourceBranch'")
        val target = strike.input["targetBranch"] as? String ?: error("missing input 'targetBranch'")
        val title = strike.input["title"] as? String ?: error("missing input 'title'")
        return StrikeResult(strike.id, output = client.openMergeRequest(projectId, source, target, title))
    }
}
