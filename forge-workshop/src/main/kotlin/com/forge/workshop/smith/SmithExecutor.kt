package com.forge.workshop.smith

import com.forge.sdk.capability.Capability
import com.forge.sdk.capability.CapabilityId
import com.forge.sdk.capability.ExecutorKind
import com.forge.sdk.capability.ExecutorProvider
import com.forge.sdk.capability.ProviderId
import com.forge.sdk.context.Stock
import com.forge.sdk.domain.StrikeDecl
import com.forge.sdk.domain.StrikeResult
import com.forge.workshop.llm.LlmClient
import com.forge.workshop.llm.LlmProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * A Smith — the LLM executor. Runs a Strike by dispatching over the skill catalog: the model is
 * handed the L1 catalog (name — description) and, via a small JSON protocol, may `open_skill` to
 * pull a SKILL.md body into context (progressive disclosure) before returning a `final` result.
 *
 * This is the "skills, not RAG" seam: knowledge is pulled on demand, not stuffed up front. Capability
 * calls from within the Smith (side-effecting actions) route back through the engine — added next.
 */
class SmithExecutor(
    private val cap: CapabilityId,
    private val llm: LlmClient,
    private val profile: LlmProfile,
    private val catalog: String,
    private val loadSkill: (String) -> String?,
    private val role: String = "локальная модель",
    private val maxSteps: Int = 4,
) : ExecutorProvider {

    override val id = ProviderId("smith.${cap.value}")
    override val kind = ExecutorKind.SMITH
    override val capabilities = setOf(Capability(cap))

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(strike: StrikeDecl, stock: Stock): StrikeResult {
        val goal = (strike.input["goal"] as? String)?.takeIf { it.isNotBlank() }
            ?: "Выполни шаг «${cap.value}» и верни краткий результат."
        val opened = StringBuilder()

        repeat(maxSteps) {
            val reply = llm.ask(system(opened.toString()), goal, profile)
            val action = parse(reply)
            when (action) {
                is Action.Open -> {
                    val body = loadSkill(action.skill)
                    if (body != null && !opened.contains("# Навык: ${action.skill}")) {
                        opened.append("\n\n# Навык: ${action.skill}\n").append(body)
                    } else {
                        // Unknown/duplicate skill — stop looping and take the reply as the answer.
                        return StrikeResult(strike.id, output = action.raw)
                    }
                }
                is Action.Final -> return StrikeResult(strike.id, output = action.result)
                null -> return StrikeResult(strike.id, output = reply.trim())
            }
        }
        return StrikeResult(strike.id, output = "готово (лимит шагов)")
    }

    private fun system(opened: String): String = buildString {
        append("Ты — Smith ($role), исполнитель шага в The Forge.\n")
        append("Тебе доступны навыки (skills) — памятки о том, как и где искать информацию.\n")
        append("Сначала реши, нужен ли навык. Отвечай СТРОГО одним JSON-объектом, без markdown и пояснений:\n")
        append("- открыть навык: {\"action\":\"open_skill\",\"skill\":\"<точное имя из каталога>\"}\n")
        append("- когда ответ готов: {\"action\":\"final\",\"result\":\"<результат шага>\"}\n\n")
        append("Каталог навыков:\n")
        append(catalog.ifBlank { "(пусто)" })
        append("\n\nУже открытые навыки:\n")
        append(opened.ifBlank { "(нет)" })
    }

    private sealed interface Action {
        data class Open(val skill: String, val raw: String) : Action
        data class Final(val result: String) : Action
    }

    private fun parse(raw: String): Action? {
        val text = raw.replace("```json", "").replace("```", "").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
        return when ((obj["action"] as? JsonPrimitive)?.content) {
            "open_skill" -> (obj["skill"] as? JsonPrimitive)?.content?.let { Action.Open(it, text) }
            "final" -> Action.Final((obj["result"] as? JsonPrimitive)?.content ?: text)
            else -> null
        }
    }
}
