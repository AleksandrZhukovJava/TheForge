package com.forge.executors.secret

import com.forge.sdk.secret.MissingSecretException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InMemorySecretStoreTest {

    @Test fun `put get remove round-trips`() = runBlocking {
        val store = InMemorySecretStore()
        store.put("gitlab.token", "abc")
        assertEquals("abc", store.get("gitlab.token"))
        store.remove("gitlab.token")
        assertNull(store.get("gitlab.token"))
    }

    @Test fun `require throws for a missing secret`() = runBlocking {
        assertFailsWith<MissingSecretException> { InMemorySecretStore().require("nope") }
    }
}
