package com.forge.sdk.security

/**
 * What a plugin is allowed to touch. A plugin declares the set it needs; Forge Core (later) grants
 * only what was declared and denies the rest. Declaring intent here is what makes integrations
 * "managed" rather than ambient.
 */
sealed interface Permission {
    /** Outbound network to a specific host, e.g. Network("gitlab.com"). */
    data class Network(val host: String) : Permission

    /** Read a secret by key, e.g. Secret("gitlab.token"). */
    data class Secret(val key: String) : Permission

    /** Run a local process, e.g. Exec("git"). */
    data class Exec(val command: String) : Permission
}

/** Implemented by providers/plugins that need explicit permissions. */
interface RequiresPermissions {
    val requiredPermissions: Set<Permission>
}
