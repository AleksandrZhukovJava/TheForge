package com.forge.sdk.common

/**
 * Serializable reference to an input/output type of a capability contract.
 *
 * Deliberately a name-based ref (not a reflective KType): Skills/Recipes are serializable
 * data (decision Q5), so type contracts must survive serialization and cross a plugin boundary.
 */
@JvmInline
value class TypeRef(val name: String) {
    companion object {
        val ANY = TypeRef("kotlin.Any")
        val UNIT = TypeRef("kotlin.Unit")

        inline fun <reified T> of(): TypeRef = TypeRef(T::class.qualifiedName ?: "kotlin.Any")
    }
}
