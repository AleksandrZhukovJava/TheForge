package com.forge.executors.secret

import com.forge.sdk.secret.SecretStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * File-backed [SecretStore] so tokens survive restarts (the app is usable day-to-day without
 * re-entering them). Plaintext `.properties` for now — an OS-keychain-backed store is the planned
 * upgrade; the interface stays the same.
 */
class FileSecretStore(private val file: Path) : SecretStore {

    private val props = Properties()

    init {
        if (Files.exists(file)) {
            Files.newInputStream(file).use { props.load(it) }
        }
    }

    override suspend fun get(key: String): String? =
        synchronized(props) { props.getProperty(key) }

    override suspend fun put(key: String, value: String) =
        synchronized(props) {
            props.setProperty(key, value)
            persist()
        }

    override suspend fun remove(key: String) =
        synchronized(props) {
            props.remove(key)
            persist()
        }

    private fun persist() {
        file.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(file).use { props.store(it, "The Forge secrets — do not share") }
    }
}
