package com.forge.executors.secret

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileSecretStoreTest {

    @Test fun `persists across instances`() = runBlocking {
        val file = Files.createTempFile("forge-secret", ".properties")
        try {
            FileSecretStore(file).put("jira.token", "abc123")
            // A fresh instance reads what the previous one wrote.
            assertEquals("abc123", FileSecretStore(file).get("jira.token"))

            FileSecretStore(file).remove("jira.token")
            assertNull(FileSecretStore(file).get("jira.token"))
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
