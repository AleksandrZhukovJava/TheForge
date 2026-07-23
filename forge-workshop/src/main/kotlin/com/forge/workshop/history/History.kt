package com.forge.workshop.history

import androidx.compose.runtime.mutableStateListOf
import com.forge.workshop.ui.PillStatus
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class HistoryEntry(val title: String, val time: String, val status: PillStatus)

/** In-memory log of Skill runs shown on the History screen. (Persisted store comes later.) */
class HistoryStore {
    val entries = mutableStateListOf<HistoryEntry>()

    fun record(title: String, ok: Boolean) {
        entries.add(0, HistoryEntry(title, LocalTime.now().format(TIME), if (ok) PillStatus.DONE else PillStatus.FAILED))
        while (entries.size > 50) entries.removeAt(entries.lastIndex)
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
