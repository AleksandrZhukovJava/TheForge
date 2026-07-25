package com.forge.workshop.skills

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Parsed YAML frontmatter of a SKILL.md — the cheap L1 the LLM/UI sees before opening the body. */
data class SkillManifest(
    val name: String,
    val description: String,
    val allowedCapabilities: List<String> = emptyList(),
)

/** A skill on disk: a folder holding a SKILL.md (frontmatter + markdown body). */
data class Skill(
    val id: String,          // folder name
    val project: String,
    val manifest: SkillManifest,
    val body: String,
)

/**
 * File-backed skill library — classic Agent-Skills layout, one folder per project:
 * `<root>/<project>/<skill-id>/SKILL.md`. Human-editable, git-friendly. Progressive disclosure:
 * [list]/[catalog] give name+description only; [load] reads the full body on demand.
 */
class SkillStore(private val root: Path) {

    fun projects(): List<String> {
        val names = if (root.exists()) root.listDirectoryEntries().filter { it.isDirectory() }.map { it.name } else emptyList()
        return (names + DEFAULT).distinct().sorted()
    }

    fun ensureProject(project: String) {
        Files.createDirectories(root.resolve(project.ifBlank { DEFAULT }))
    }

    fun list(project: String): List<Skill> {
        val dir = root.resolve(project.ifBlank { DEFAULT })
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries().filter { it.isDirectory() }.mapNotNull { folder ->
            val md = folder.resolve(SKILL_FILE)
            if (!md.exists()) return@mapNotNull null
            runCatching { parse(folder.name, project, md.readText()) }.getOrNull()
        }.sortedBy { it.manifest.name.lowercase(Locale.ROOT) }
    }

    fun load(project: String, id: String): Skill? {
        val md = root.resolve(project).resolve(id).resolve(SKILL_FILE)
        return if (md.exists()) runCatching { parse(id, project, md.readText()) }.getOrNull() else null
    }

    /** Create a new skill folder; returns its id. */
    fun create(project: String, name: String, description: String, body: String, allowedCapabilities: List<String> = emptyList()): String {
        val id = slug(name)
        write(project, id, SkillManifest(name.trim().ifBlank { "Навык" }, description.trim(), allowedCapabilities), body)
        return id
    }

    fun save(project: String, id: String, manifest: SkillManifest, body: String) = write(project, id, manifest, body)

    fun delete(project: String, id: String) {
        val dir = root.resolve(project).resolve(id)
        if (dir.exists()) dir.toFile().deleteRecursively()
    }

    /** L1 catalog string for an LLM system prompt (name — description per skill). */
    fun catalog(project: String): String =
        list(project).joinToString("\n") { "- ${it.manifest.name} — ${it.manifest.description}" }

    private fun write(project: String, id: String, manifest: SkillManifest, body: String) {
        val dir = root.resolve(project.ifBlank { DEFAULT }).resolve(id)
        Files.createDirectories(dir)
        dir.resolve(SKILL_FILE).writeText(format(manifest, body))
    }

    private fun slug(name: String): String {
        val base = name.lowercase(Locale.ROOT).map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
            .trim('-').replace(Regex("-+"), "-").take(40).ifBlank { "skill" }
        return "$base-${UUID.randomUUID().toString().take(6)}"
    }

    companion object {
        const val DEFAULT = "default"
        private const val SKILL_FILE = "SKILL.md"

        /** Render a SKILL.md: YAML frontmatter + markdown body. */
        fun format(m: SkillManifest, body: String): String = buildString {
            append("---\n")
            append("name: ${m.name}\n")
            append("description: ${m.description}\n")
            if (m.allowedCapabilities.isNotEmpty()) append("allowed-capabilities: [${m.allowedCapabilities.joinToString(", ")}]\n")
            append("---\n\n")
            append(body.trimEnd())
            append("\n")
        }

        /** Parse a SKILL.md. Tolerant of a missing/partial frontmatter block. */
        fun parse(id: String, project: String, text: String): Skill {
            val normalized = text.replace("\r\n", "\n")
            var name = id
            var description = ""
            var caps = emptyList<String>()
            var body = normalized
            if (normalized.startsWith("---")) {
                val end = normalized.indexOf("\n---", 3)
                if (end >= 0) {
                    val front = normalized.substring(3, end).trim('\n')
                    body = normalized.substring(end + 4).trimStart('\n')
                    front.lineSequence().forEach { line ->
                        val i = line.indexOf(':')
                        if (i <= 0) return@forEach
                        val key = line.substring(0, i).trim()
                        val value = line.substring(i + 1).trim()
                        when (key) {
                            "name" -> name = value.ifBlank { id }
                            "description" -> description = value
                            "allowed-capabilities" -> caps = value.trim('[', ']').split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        }
                    }
                }
            }
            return Skill(id, project, SkillManifest(name, description, caps), body.trim())
        }
    }
}
