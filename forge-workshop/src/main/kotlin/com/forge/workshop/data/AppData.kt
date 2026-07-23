package com.forge.workshop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Serializable
enum class Priority {
    NONE, LOW, MEDIUM, HIGH;

    /** Cycle for the click-to-change chip. */
    fun next(): Priority = entries[(ordinal + 1) % entries.size]
}

@Serializable
data class LocalTask(
    val id: String,
    val summary: String,
    val priority: Priority = Priority.NONE,
)

@Serializable
data class AppData(
    val localTasks: List<LocalTask> = emptyList(),
    /** Priority override for Jira issues, keyed by issue key. */
    val jiraPriority: Map<String, Priority> = emptyMap(),
)

/**
 * Persistent local data (own tasks + priority overlays), JSON-backed. Observable so the Bench
 * recomposes on change. The foundation for the migrated widget features (blocks, overlays, …).
 */
class AppDataStore(private val file: Path) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    var data by mutableStateOf(load())
        private set

    private fun load(): AppData = try {
        if (Files.exists(file)) json.decodeFromString(Files.readString(file)) else AppData()
    } catch (_: Exception) {
        AppData()
    }

    private fun update(block: (AppData) -> AppData) {
        data = block(data)
        try {
            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, json.encodeToString(data))
        } catch (_: Exception) {
            // best-effort persistence; keep the in-memory state either way
        }
    }

    fun addLocalTask(summary: String) {
        val trimmed = summary.trim()
        if (trimmed.isEmpty()) return
        update { it.copy(localTasks = it.localTasks + LocalTask(UUID.randomUUID().toString(), trimmed)) }
    }

    fun updateLocalTask(id: String, summary: String) =
        update { d -> d.copy(localTasks = d.localTasks.map { if (it.id == id) it.copy(summary = summary.trim()) else it }) }

    fun deleteLocalTask(id: String) =
        update { d -> d.copy(localTasks = d.localTasks.filterNot { it.id == id }) }

    fun cycleLocalPriority(id: String) =
        update { d -> d.copy(localTasks = d.localTasks.map { if (it.id == id) it.copy(priority = it.priority.next()) else it }) }

    fun cycleJiraPriority(key: String) =
        update { d ->
            val current = d.jiraPriority[key] ?: Priority.NONE
            d.copy(jiraPriority = d.jiraPriority + (key to current.next()))
        }

    fun jiraPriority(key: String): Priority = data.jiraPriority[key] ?: Priority.NONE
}
