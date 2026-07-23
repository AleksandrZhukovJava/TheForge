package com.forge.brain.policy

import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.policy.Policy
import com.forge.sdk.policy.Quota

/**
 * The bundled default rule set. Later externalised to a signed file loaded by Forge Core; for now
 * it lives in code so it is compiled-in and testable. Mirrors POLICY.md.
 */
val DefaultPolicy = Policy(
    // --- Запрещено (никогда не выполняется) ---
    forbidden = setOf(
        CapabilityId("gitlab.merge"),
        CapabilityId("gitlab.force-push"),
        CapabilityId("vcs.hard-delete"),
    ),
    // --- Лимиты ---
    quotas = listOf(
        Quota(CapabilityId("jira.create-issue"), max = 1),
    ),
    // --- Требует подтверждения (помимо danger=CONFIRM на самом Strike) ---
    confirm = setOf(
        CapabilityId("vcs.publish-review"),
    ),
)
