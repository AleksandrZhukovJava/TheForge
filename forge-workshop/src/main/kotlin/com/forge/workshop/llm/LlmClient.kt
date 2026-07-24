package com.forge.workshop.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Endpoint profile: base URL (…/v1), model id (blank = auto-pick), optional API key. */
data class LlmProfile(val baseUrl: String, val model: String, val apiKey: String?)

data class LlmResult(val summary: String, val description: String)

@Serializable private data class ChatMessage(val role: String, val content: String)
@Serializable private data class ChatRequest(val model: String, val messages: List<ChatMessage>, val temperature: Double = 0.3)
@Serializable private data class ChatChoice(val message: ChatMessage? = null)
@Serializable private data class ChatResponse(val choices: List<ChatChoice> = emptyList(), val error: ChatError? = null)
@Serializable private data class ChatError(val message: String? = null)
@Serializable private data class ModelsResponse(val data: List<ModelInfo> = emptyList())
@Serializable private data class ModelInfo(val id: String? = null)
@Serializable private data class TaskJson(val summary: String? = null, val description: String? = null)

/**
 * OpenAI-compatible chat client — copied from the working widget. Covers a local server
 * (LM Studio / Ollama) or a remote OpenAI-compatible endpoint. Blank model → auto-pick the
 * first model the server reports (LM Studio JIT).
 */
class LlmClient(private val http: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val autoModel = mutableMapOf<String, String>()

    /** Turn a free-form description into {summary, description}, following [promptTemplate]. */
    suspend fun generateTask(userText: String, profile: LlmProfile, promptTemplate: String): LlmResult {
        val system = listOf(
            "Ты помощник, который оформляет задачи для Jira на русском языке.",
            "Пользователь описывает задачу своими словами. Верни СТРОГО JSON без пояснений, без markdown, без текста до или после:",
            "{\"summary\": \"<краткий заголовок задачи, до 100 символов>\", \"description\": \"<оформленное описание>\"}",
            "Переводы строк внутри значений экранируй как \\n.",
            "Описание оформи ровно по этому формату (если данных для раздела нет — напиши \"уточнить\"):",
            promptTemplate,
        ).joinToString("\n\n")
        val raw = chat(listOf(ChatMessage("system", system), ChatMessage("user", userText)), profile)
        return parse(raw)
    }

    /** Apply a follow-up instruction to the current draft, changing only what's asked. */
    suspend fun refineTask(instruction: String, current: LlmResult, profile: LlmProfile, promptTemplate: String): LlmResult {
        val system = listOf(
            "Ты редактируешь черновик задачи для Jira на русском языке.",
            "Тебе дают текущий заголовок (summary), описание (description) и указание, что изменить.",
            "Внеси ТОЛЬКО запрошенное изменение; всё остальное сохрани без изменений, дословно.",
            "Верни СТРОГО JSON без пояснений, без markdown:",
            "{\"summary\": \"<заголовок>\", \"description\": \"<описание целиком, с внесённой правкой>\"}",
            "Переводы строк внутри значений экранируй как \\n.",
            "Эталонная структура описания (для ориентира, не перестраивай без запроса):",
            promptTemplate,
        ).joinToString("\n\n")
        val userMsg = listOf(
            "Текущий заголовок: ${current.summary.ifBlank { "(пусто)" }}",
            "Текущее описание:\n${current.description.ifBlank { "(пусто)" }}",
            "Что изменить: $instruction",
        ).joinToString("\n\n")
        val raw = chat(listOf(ChatMessage("system", system), ChatMessage("user", userMsg)), profile)
        val parsed = parse(raw)
        return if (parsed.summary.isBlank() && parsed.description.isBlank()) current else parsed
    }

    /** Cheap connectivity check for the settings "Проверить" button. */
    suspend fun test(profile: LlmProfile): String {
        chat(listOf(ChatMessage("user", "Ответь одним словом: ок")), profile)
        return "ок"
    }

    private suspend fun chat(messages: List<ChatMessage>, profile: LlmProfile): String {
        val model = resolveModel(profile)
        val payload = json.encodeToString(ChatRequest(model, messages))
        val text = try {
            http.post("${profile.baseUrl}/chat/completions") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                if (!profile.apiKey.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer ${profile.apiKey}")
                setBody(payload)
            }.bodyAsText()
        } catch (e: Exception) {
            throw IllegalStateException("Нет связи с LLM (${profile.baseUrl}) — запущен ли сервер? ${e.message}")
        }
        val resp = try {
            json.decodeFromString<ChatResponse>(text)
        } catch (e: Exception) {
            throw IllegalStateException("LLM вернула не то: ${text.take(200)}")
        }
        val content = resp.choices.firstOrNull()?.message?.content
        if (content.isNullOrBlank()) throw IllegalStateException(resp.error?.message ?: "LLM вернула пустой ответ")
        return content
    }

    private suspend fun resolveModel(profile: LlmProfile): String {
        if (profile.model.isNotBlank()) return profile.model
        autoModel[profile.baseUrl]?.let { return it }
        return try {
            val text = http.get("${profile.baseUrl}/models") {
                if (!profile.apiKey.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer ${profile.apiKey}")
            }.bodyAsText()
            val id = json.decodeFromString<ModelsResponse>(text).data.firstOrNull()?.id
            (id ?: "local-model").also { if (id != null) autoModel[profile.baseUrl] = id }
        } catch (e: Exception) {
            "local-model"
        }
    }

    private fun parse(raw: String): LlmResult {
        val text = raw.replace("```json", "").replace("```", "").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching { json.decodeFromString<TaskJson>(text.substring(start, end + 1)) }
                .getOrNull()
                ?.let { obj ->
                    if (!obj.summary.isNullOrBlank() || !obj.description.isNullOrBlank()) {
                        return LlmResult(obj.summary.orEmpty(), obj.description.orEmpty())
                    }
                }
        }
        val lines = text.lines()
        val summary = lines.firstOrNull().orEmpty().removePrefix("#").trim().take(150)
        val desc = lines.drop(1).joinToString("\n").trim().ifEmpty { text }
        return LlmResult(summary, desc)
    }
}
