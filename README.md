# The Forge

> Инженерная платформа, где LLM не видно: пользователь запускает **Skills**, а платформа сама
> собирает контекст, выбирает исполнителя и экономит токены.

Кросс-платформенный десктоп (**Windows · macOS · Linux**) на **Kotlin + Compose Multiplatform**.
Развивает прежний Jira/GitLab-виджет, поглощая его как набор Skills.

## Ключевые идеи

- **Skill First / AI Invisible** — работаешь со Skills, а не с моделями.
- **Automation First** — сначала детерминизм, LLM только когда нужен интеллект.
- **Диспетчеризация Skills, не RAG** — знания доступны как вызываемые Skills.
- **Everything is a Plugin** — всё грузится через SDK, ядро не знает конкретики.

## Словарь

| Роль | Имя |
|---|---|
| Атомарный шаг | **Strike** |
| Процесс Skill | **Recipe** (DAG) |
| Собранный контекст | **Stock** |
| Исполнители (Automation First) | **Tool → Press → Master → Smith** |

## Модули

| Модуль | Назначение | Статус |
|---|---|---|
| `forge-sdk` | Публичные контракты (нулевые зависимости) | ✅ |
| `forge-brain` | Оркестратор: Capability Registry + StrikeResolver | ✅ движок |
| `forge-core` | Плагины, DI, конфиг, события, безопасность, аудит | ⏳ |
| `forge-anvil` · `forge-memory` · `forge-executors` · `plugins/*` · `forge-workshop` | контекст, память, исполнители, интеграции, UI | ⏳ |

## Сборка и тесты

```bash
./gradlew :forge-brain:test
```

Движок (Capability Registry + StrikeResolver) покрыт 21 тестом — вся 15-кейсовая матрица выбора
исполнителя, реестр и детерминизм.

## Документация

- [Архитектура (v1.1)](THE_FORGE_ARCHITECTURE.md) — слои, контракты, механизмы, фазы MVP.

## Стек

Kotlin 2.0 / JVM 17 · Gradle (wrapper) · Compose Desktop · Koin · Ktor · GitHub Actions (матрица win/mac).
