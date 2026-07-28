package com.forge.workshop.updater

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Current app version — the single source is `appVersion` in forge-workshop/build.gradle.kts, which
 * generates the `forge-version.txt` resource read here. Falls back to 0.0.0 if the resource is absent.
 */
object AppVersion {
    val CURRENT: String = runCatching {
        AppVersion::class.java.getResourceAsStream("/forge-version.txt")?.bufferedReader()?.use { it.readText().trim() }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"
}

/** The public repo whose GitHub Releases feed updates. */
private const val REPO = "AleksandrZhukovJava/TheForge"
private const val UA = "TheForge-Updater"

data class UpdateInfo(val version: String, val downloadUrl: String, val notes: String)

@Serializable private data class GhAsset(val name: String = "", @SerialName("browser_download_url") val url: String = "")
@Serializable private data class GhRelease(
    @SerialName("tag_name") val tag: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

/**
 * Free self-hosted updater: checks the repo's latest GitHub Release, and if it's newer than
 * [AppVersion.CURRENT], downloads its installer and launches it. No Conveyor, no signing — the OS
 * may warn on first run, which is fine for a small circle. Returns null on any failure (offline,
 * no releases yet, no installer asset) so the UI simply shows nothing.
 */
object Updater {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(): UpdateInfo? {
        val http = HttpClient(CIO)
        return try {
            val resp = http.get("https://api.github.com/repos/$REPO/releases/latest") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, UA)
            }
            if (!resp.status.isSuccess()) return null
            val rel = json.decodeFromString<GhRelease>(resp.bodyAsText())
            if (rel.draft || rel.prerelease) return null
            val remote = rel.tag.trimStart('v', 'V').trim()
            if (remote.isBlank() || !isNewer(remote, AppVersion.CURRENT)) return null
            val asset = rel.assets.firstOrNull { it.name.endsWith(".msi", true) }
                ?: rel.assets.firstOrNull { it.name.endsWith(".exe", true) }
                ?: return null
            UpdateInfo(remote, asset.url, rel.body)
        } catch (_: Exception) {
            null
        } finally {
            http.close()
        }
    }

    /** Download the installer to the temp dir and return the file, reporting 0..1 progress. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit = {}): File {
        val http = HttpClient(CIO) { engine { requestTimeout = 0 } } // 0 = no timeout for a large download
        try {
            val bytes: ByteArray = http.get(info.downloadUrl) {
                header(HttpHeaders.UserAgent, UA)
                onDownload { sent, total -> if (total != null && total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f)) }
            }.body()
            val ext = if (info.downloadUrl.endsWith(".exe", true)) "exe" else "msi"
            val out = File(System.getProperty("java.io.tmpdir"), "TheForge-${info.version}.$ext")
            out.writeBytes(bytes)
            return out
        } finally {
            http.close()
        }
    }

    /**
     * Apply the update Steam-style — no visible Windows dialogs. A hidden helper (wscript) waits for
     * this app to exit, runs the MSI fully silently (`/qn`), then relaunches the app. The caller must
     * quit right after. Falls back to a basic-UI install if the app exe can't be located.
     */
    fun launchInstaller(file: File) {
        if (!file.extension.equals("msi", true)) {
            try { ProcessBuilder(file.absolutePath, "/SILENT").start() } catch (_: Exception) { runCatching { java.awt.Desktop.getDesktop().open(file) } }
            return
        }
        val exe = currentExe()
        if (exe != null) {
            runCatching {
                val vbs = File(System.getProperty("java.io.tmpdir"), "theforge-update.vbs")
                vbs.writeText(silentUpdateVbs(file.absolutePath, exe))
                ProcessBuilder("wscript.exe", vbs.absolutePath).start()
            }.onFailure {
                runCatching { ProcessBuilder("msiexec", "/i", file.absolutePath, "/qb", "/norestart").start() }
            }
        } else {
            // Dev run (launcher is java.exe) or unknown location — visible basic-UI install.
            runCatching { ProcessBuilder("msiexec", "/i", file.absolutePath, "/qb", "/norestart").start() }
                .onFailure { runCatching { java.awt.Desktop.getDesktop().open(file) } }
        }
    }

    /** Path of the running packaged launcher, if we're the installed app (not a dev java run). */
    private fun currentExe(): String? =
        runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
            ?.takeIf { it.endsWith("TheForge.exe", ignoreCase = true) }

    /**
     * VBScript run windowless (via wscript): poll until TheForge.exe has fully exited (so the file
     * is unlocked), install silently, then relaunch. Robust to how long the app takes to quit.
     */
    private fun silentUpdateVbs(msi: String, exe: String): String {
        val nl = "\r\n"
        return buildString {
            append("Set sh = CreateObject(\"WScript.Shell\")").append(nl)
            append("Set wmi = GetObject(\"winmgmts:\\\\.\\root\\cimv2\")").append(nl)
            append("Dim i").append(nl)
            append("For i = 1 To 60").append(nl)
            append("  If wmi.ExecQuery(\"Select ProcessId From Win32_Process Where Name='TheForge.exe'\").Count = 0 Then Exit For").append(nl)
            append("  WScript.Sleep 500").append(nl)
            append("Next").append(nl)
            append("sh.Run \"msiexec /i \"\"").append(msi).append("\"\" /qn /norestart\", 0, True").append(nl)
            append("WScript.Sleep 800").append(nl)
            append("sh.Run \"\"\"").append(exe).append("\"\"\", 1, False").append(nl)
        }
    }

    /** True if version [a] is strictly newer than [b] (numeric dotted compare). */
    fun isNewer(a: String, b: String): Boolean {
        val pa = a.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
