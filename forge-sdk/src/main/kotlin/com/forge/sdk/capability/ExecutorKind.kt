package com.forge.sdk.capability

/**
 * The four kinds of executor, in Automation-First priority order.
 *
 * [order] IS the "rule of executor selection": the resolver prefers the lowest order, so a Strike
 * only falls to a [SMITH] (LLM) when no [TOOL], [PRESS] or [MASTER] can handle it.
 *
 * Forge vocabulary: Tool (instrument) -> Press (machine) -> Master (the smith's own hand) ->
 * Smith (the forge fire — intelligence, last resort).
 */
enum class ExecutorKind(val order: Int) {
    /** Deterministic action, never uses an LLM (GitLab/Jira/Grafana API, Git, ...). */
    TOOL(0),

    /** Runs a local program (Gradle, Maven, Bash, Python, ...). */
    PRESS(1),

    /** Requires the user — confirm a merge, resolve a conflict, choose a branch. */
    MASTER(2),

    /** Intelligent executor backed by an LLM (local model or agent). */
    SMITH(3),
}

/** How dangerous a Strike is, driving confirmation and hard bans. */
enum class DangerLevel {
    /** Read-only / reversible — runs without confirmation. */
    SAFE,

    /** Side-effecting — requires a Master confirmation before EXECUTE. */
    CONFIRM,

    /** Must never run (e.g. GitLab merge / force-push). Short-circuits resolution. */
    FORBIDDEN,
}
