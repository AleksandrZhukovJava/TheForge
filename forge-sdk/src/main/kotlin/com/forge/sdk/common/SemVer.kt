package com.forge.sdk.common

/** Minimal semantic version used to version capability contracts. */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** Parses "2", "2.1", "2.1.3". Missing components default to 0. */
        fun parse(text: String): SemVer {
            val parts = text.trim().split('.')
            fun at(i: Int) = parts.getOrNull(i)?.toIntOrNull() ?: 0
            return SemVer(at(0), at(1), at(2))
        }
    }
}

/**
 * Version constraint a Strike may put on the capability it requests.
 * The default ([AnyVersion]) matches everything — full range support is a hook for later.
 */
sealed interface VersionConstraint {
    fun allows(version: SemVer): Boolean
}

data object AnyVersion : VersionConstraint {
    override fun allows(version: SemVer): Boolean = true
}

/** `^base` — same major and at least [base] (e.g. ^2.0.0 allows 2.x.y, rejects 1.x and 3.x). */
data class CaretRange(val base: SemVer) : VersionConstraint {
    override fun allows(version: SemVer): Boolean =
        version.major == base.major && version >= base
}
