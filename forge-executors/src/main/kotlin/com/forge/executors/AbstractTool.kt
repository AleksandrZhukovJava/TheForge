package com.forge.executors

import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId

/**
 * Convenience base for deterministic Tool executors (GitLab/Jira/Grafana API, Git, ...).
 * Fixes [kind] to TOOL; subclasses implement [execute]. Never touches an LLM.
 */
abstract class AbstractTool(
    override val id: ProviderId,
    override val capabilities: Set<Capability>,
    override val priority: Int = 0,
) : ExecutorProvider {
    final override val kind: ExecutorKind = ExecutorKind.TOOL
}
