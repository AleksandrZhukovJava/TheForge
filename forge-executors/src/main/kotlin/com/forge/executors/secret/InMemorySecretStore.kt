package com.forge.executors.secret

import com.forge.sdk.secret.SecretStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Dev/test [SecretStore] kept in memory. The OS-keychain-backed store replaces it at packaging;
 * everything above the interface is identical, so integrations never change.
 */
class InMemorySecretStore(initial: Map<String, String> = emptyMap()) : SecretStore {

    private val secrets = ConcurrentHashMap<String, String>(initial)

    override suspend fun get(key: String): String? = secrets[key]

    override suspend fun put(key: String, value: String) {
        secrets[key] = value
    }

    override suspend fun remove(key: String) {
        secrets.remove(key)
    }
}
