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

/** A named group of statuses — a column on the Bench. Local tasks match the literal "Своя задача". */
@Serializable
data class TaskBlock(
    val id: String,
    val name: String,
    val statuses: List<String> = emptyList(),
)

fun defaultBlocks(): List<TaskBlock> = listOf(
    TaskBlock("in-progress", "В работе", listOf("In Progress", "В работе", "In Development")),
    TaskBlock("review", "Ревью", listOf("Review", "Code Review", "In Review", "Ревью", "На проверке")),
    TaskBlock("todo", "К выполнению", listOf("To Do", "Open", "Backlog", "Selected for Development", "К выполнению")),
    TaskBlock("local", "Свои", listOf("Своя задача")),
)

@Serializable
data class AppData(
    val localTasks: List<LocalTask> = emptyList(),
    /** Priority override for Jira issues, keyed by issue key. */
    val jiraPriority: Map<String, Priority> = emptyMap(),
    /** Task ids/keys marked «текущая» (highlighted). */
    val current: Set<String> = emptySet(),
    /** Task ids/keys marked blocked. */
    val blocked: Set<String> = emptySet(),
    /** Task ids/keys marked done — a local overlay; a Jira issue is never changed in Jira. */
    val done: Set<String> = emptySet(),
    /** Status columns for the Bench. */
    val blocks: List<TaskBlock> = defaultBlocks(),
)

/** Literal status assigned to local tasks so they fall into the matching block. */
const val LOCAL_STATUS = "Своя задача"

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

    fun toggleCurrent(id: String) = update { d ->
        // «текущая» is cleared when a task is blocked or done.
        d.copy(current = if (id in d.current) d.current - id else d.current + id)
    }

    fun toggleBlocked(id: String) = update { d ->
        val nowBlocked = id !in d.blocked
        d.copy(
            blocked = if (nowBlocked) d.blocked + id else d.blocked - id,
            current = if (nowBlocked) d.current - id else d.current,
        )
    }

    fun toggleDone(id: String) = update { d ->
        val nowDone = id !in d.done
        d.copy(
            done = if (nowDone) d.done + id else d.done - id,
            current = if (nowDone) d.current - id else d.current,
        )
    }

    // --- Blocks config ---

    fun addBlock() = update { it.copy(blocks = it.blocks + TaskBlock(UUID.randomUUID().toString(), "Новый блок")) }

    fun deleteBlock(id: String) = update { d -> d.copy(blocks = d.blocks.filterNot { it.id == id }) }

    fun setBlockName(id: String, name: String) =
        update { d -> d.copy(blocks = d.blocks.map { if (it.id == id) it.copy(name = name) else it }) }

    fun setBlockStatuses(id: String, csv: String) =
        update { d ->
            val statuses = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            d.copy(blocks = d.blocks.map { if (it.id == id) it.copy(statuses = statuses) else it })
        }

    fun resetBlocks() = update { it.copy(blocks = defaultBlocks()) }
}
