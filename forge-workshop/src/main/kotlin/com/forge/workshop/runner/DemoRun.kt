package com.forge.workshop.runner

import com.forge.brain.resolve.CapabilityRegistry
import com.forge.brain.resolve.DefaultCapabilityRegistry
import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.DangerLevel
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeId
import com.forge.sdk.domain.StrikeResult
import com.forge.workshop.foundry.ExecTag
import kotlinx.coroutines.delay

/** A display step of a demo run, paired with the real [StrikeDecl] the engine executes. */
class DemoStrike(
    val name: String,
    val executorLabel: String,
    val tag: ExecTag,
    val decl: StrikeDecl,
)

class DemoRun(
    val title: String,
    val steps: List<DemoStrike>,
    val registry: CapabilityRegistry,
)

/** Fake provider: real engine machinery, canned output after a small delay to animate the timeline. */
private class DemoProvider(
    id: String,
    override val kind: ExecutorKind,
    capability: CapabilityId,
    private val delayMs: Long,
    private val output: String,
) : ExecutorProvider {
    override val id = ProviderId(id)
    override val capabilities = setOf(Capability(capability))
    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        delay(delayMs)
        return StrikeResult(strike.id, output = output)
    }
}

private class Blueprint(
    val name: String,
    val executorLabel: String,
    val tag: ExecTag,
    val capability: String,
    val kind: ExecutorKind,
    val danger: DangerLevel = DangerLevel.SAFE,
    val delayMs: Long = 700,
    val output: String = "ok",
)

/**
 * Builds a runnable demo recipe for a Skill. Providers are fakes (no live tokens yet), but the
 * resolve → confirm → execute path is the real engine — including the Master gate on CONFIRM steps.
 */
fun buildDemoRun(title: String): DemoRun {
    val blueprints = when (title) {
        "Review Pull Request" -> listOf(
            Blueprint("Load Merge Request", "GitLab Tool", ExecTag.TOOL, "vcs.load-mr", ExecutorKind.TOOL, output = "MR !41"),
            Blueprint("Load Related Jira", "Jira Tool", ExecTag.TOOL, "issues.load-jira", ExecutorKind.TOOL, output = "FORGE-9"),
            Blueprint("Search Architecture Memory", "Forge Memory", ExecTag.MASTER, "memory.search", ExecutorKind.TOOL, output = "2 ADR"),
            Blueprint("Generate Review", "Claude Code Smith", ExecTag.SMITH, "review.generate", ExecutorKind.SMITH, delayMs = 1100, output = "review draft"),
            Blueprint("Publish Review", "GitLab Tool", ExecTag.TOOL, "vcs.publish-review", ExecutorKind.TOOL, danger = DangerLevel.CONFIRM, output = "published"),
        )
        "Create Jira Story" -> listOf(
            Blueprint("Prepare fields", "Jira Tool", ExecTag.TOOL, "issues.prepare", ExecutorKind.TOOL),
            Blueprint("Create Issue", "Jira Tool", ExecTag.TOOL, "issues.create", ExecutorKind.TOOL, danger = DangerLevel.CONFIRM, output = "FORGE-20"),
        )
        "Open GitLab MR" -> listOf(
            Blueprint("Prepare branch diff", "GitLab Tool", ExecTag.TOOL, "vcs.prepare-mr", ExecutorKind.TOOL),
            Blueprint("Open Merge Request", "GitLab Tool", ExecTag.TOOL, "vcs.open-mr", ExecutorKind.TOOL, danger = DangerLevel.CONFIRM, output = "!45 opened"),
        )
        "Explain Service" -> listOf(
            Blueprint("Load code & docs", "Git + Confluence Tool", ExecTag.TOOL, "code.load", ExecutorKind.TOOL),
            Blueprint("Explain", "Claude Code Smith", ExecTag.SMITH, "explain.generate", ExecutorKind.SMITH, delayMs = 1000, output = "explanation"),
        )
        "Investigate Production Error" -> listOf(
            Blueprint("Load Grafana alerts", "Grafana Tool", ExecTag.TOOL, "obs.load-alerts", ExecutorKind.TOOL),
            Blueprint("Analyze", "Claude Code Smith", ExecTag.SMITH, "obs.analyze", ExecutorKind.SMITH, delayMs = 1000, output = "root cause"),
        )
        else -> listOf(
            Blueprint("Generate", "Claude Code Smith", ExecTag.SMITH, "generate.run", ExecutorKind.SMITH, delayMs = 1000, output = "generated"),
        )
    }

    val registry = DefaultCapabilityRegistry()
    blueprints.distinctBy { it.capability }.forEach { b ->
        registry.register(DemoProvider(b.capability, b.kind, CapabilityId(b.capability), b.delayMs, b.output))
    }

    val steps = blueprints.mapIndexed { i, b ->
        DemoStrike(
            name = b.name,
            executorLabel = b.executorLabel,
            tag = b.tag,
            decl = StrikeDecl(StrikeId("s$i"), CapabilityId(b.capability), b.danger),
        )
    }
    return DemoRun(title, steps, registry)
}
