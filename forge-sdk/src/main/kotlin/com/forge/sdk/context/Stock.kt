package com.forge.sdk.context

/**
 * The material an executor works on: the context Anvil forges for a Strike.
 *
 * Immutable, key-addressed. Deliberately minimal — Anvil assembles only what a Strike declared
 * it needs (no eager, RAG-style dump); heavier knowledge is pulled via callable Skills instead.
 */
class Stock(private val fragments: Map<String, Any?> = emptyMap()) {

    operator fun get(key: String): Any? = fragments[key]

    fun has(key: String): Boolean = fragments.containsKey(key)

    val keys: Set<String> get() = fragments.keys

    companion object {
        val EMPTY = Stock()
    }
}
