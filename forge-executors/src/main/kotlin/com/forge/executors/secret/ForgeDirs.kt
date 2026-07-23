package com.forge.executors.secret

import java.nio.file.Path
import java.nio.file.Paths

/** Where The Forge keeps its per-user data (tokens, settings, local overlays). */
object ForgeDirs {
    fun dataDir(): Path {
        val appData = System.getenv("APPDATA")
        return if (!appData.isNullOrBlank()) {
            Paths.get(appData, "TheForge")
        } else {
            Paths.get(System.getProperty("user.home"), ".theforge")
        }
    }
}
