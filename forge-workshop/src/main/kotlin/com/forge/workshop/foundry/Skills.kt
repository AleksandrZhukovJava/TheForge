package com.forge.workshop.foundry

/** Executor a Skill leans on — shown as a colored chip so the user reads the shape at a glance. */
enum class ExecTag(val label: String) {
    TOOL("Tool"),
    PRESS("Press"),
    MASTER("Master"),
    SMITH("Smith"),
}

/** Presentation model for a Foundry card. Later these come from the capability registry. */
data class SkillSpec(
    val title: String,
    val description: String,
    val executors: List<ExecTag>,
)

val sampleSkills = listOf(
    SkillSpec("Review Pull Request", "Собирает MR, задачу, доки и память — и пишет ревью.", listOf(ExecTag.TOOL, ExecTag.SMITH)),
    SkillSpec("Create Jira Story", "Создаёт задачу в Jira — с подтверждением.", listOf(ExecTag.TOOL, ExecTag.MASTER)),
    SkillSpec("Open GitLab MR", "Открывает merge request. Merge/force-push запрещены.", listOf(ExecTag.TOOL, ExecTag.MASTER)),
    SkillSpec("Explain Service", "Объясняет сервис по коду и документации.", listOf(ExecTag.TOOL, ExecTag.SMITH)),
    SkillSpec("Investigate Production Error", "Тянет алерты и метрики Grafana и ищет причину.", listOf(ExecTag.TOOL, ExecTag.SMITH)),
    SkillSpec("Generate Liquibase Migration", "Готовит миграцию по описанию изменений схемы.", listOf(ExecTag.SMITH)),
)
