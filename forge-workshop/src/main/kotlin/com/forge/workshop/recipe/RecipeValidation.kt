package com.forge.workshop.recipe

/** Cheap structural checks surfaced as warnings in the builder/runner (non-blocking). */
object RecipeValidation {

    fun validate(nodes: List<RecipeNode>, links: List<RecipeLink>): List<String> {
        val warnings = mutableListOf<String>()
        val startId = nodes.firstOrNull { it.typeId == START_ID }?.id
        val endId = nodes.firstOrNull { it.typeId == END_ID }?.id
        val outgoing = links.groupBy { it.from }

        if (startId != null) {
            val reachable = reach(startId, outgoing)
            nodes.filter { it.typeId != START_ID }.forEach { n ->
                if (n.id !in reachable) warnings += "«${label(n)}» недостижим от старта"
            }
            if (endId != null && endId !in reachable) warnings += "Финал недостижим от старта"
        }

        nodes.forEach { n ->
            if (n.mode == StrikeMode.AUTO) {
                val type = StrikeCatalog.byId(n.typeId) ?: return@forEach
                type.inputs.filter { it.required }.forEach { spec ->
                    if (n.inputs[spec.key].isNullOrBlank()) warnings += "«${type.name}»: не заполнено «${spec.label}»"
                }
            }
        }
        return warnings
    }

    private fun reach(start: String, outgoing: Map<String, List<RecipeLink>>): Set<String> {
        val seen = mutableSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            outgoing[cur]?.forEach { if (seen.add(it.to)) queue.add(it.to) }
        }
        return seen
    }

    private fun label(n: RecipeNode): String = when (n.typeId) {
        START_ID -> "Старт"; END_ID -> "Финал"; else -> StrikeCatalog.byId(n.typeId)?.name ?: n.typeId
    }
}
