package com.forge.sdk.secret

/**
 * Where integration tokens live. Never in code, never in plaintext config.
 *
 * The production implementation is backed by the OS secret store (Windows Credential Manager /
 * macOS Keychain) and is wired in at packaging (P4); the engine and integrations only ever see
 * this interface, so tokens stay out of source and out of logs.
 */
interface SecretStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)

    suspend fun require(key: String): String =
        get(key) ?: throw MissingSecretException(key)
}

class MissingSecretException(val key: String) :
    RuntimeException("No secret stored for key '$key'")
