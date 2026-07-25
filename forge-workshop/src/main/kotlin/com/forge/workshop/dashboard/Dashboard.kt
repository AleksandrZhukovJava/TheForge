package com.forge.workshop.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.forge.workshop.widget.WRow
import com.forge.workshop.widget.jiraRows
import com.forge.workshop.widget.mrRows
import com.forge.workshop.widget.pipelineRows
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DashboardData(
    val jira: List<WRow>,
    val mrs: List<WRow>,
    val pipelines: List<WRow>,
)

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Loaded(val data: DashboardData, val updatedAt: String) : DashboardState
    data class Error(val message: String) : DashboardState
    data object NotConfigured : DashboardState
}

/** Where the widget/popover get their data. */
interface DashboardRepository {
    suspend fun load(): DashboardData
}

/** Thrown by a repository when no integration is configured yet. */
class NotConfiguredException : Exception()

/** Placeholder data through the real refresh pipeline (so cadence + states are exercised now). */
class SampleDashboardRepository : DashboardRepository {
    override suspend fun load(): DashboardData {
        delay(400)
        return DashboardData(jiraRows, mrRows, pipelineRows)
    }
}

/**
 * Holds the current [DashboardState]; polled by the app on an interval and by manual refresh.
 *
 * Stale-while-revalidate: after the first successful load the previous data stays on screen while a
 * new poll runs, and a transient error/blip doesn't wipe it. This kills the "tasks vanish then come
 * back" flicker — [DashboardState.Loading] only shows before there is ever any data, and errors are
 * only surfaced when we have nothing better to show.
 */
class DashboardHolder(private val repo: DashboardRepository) {
    var state by mutableStateOf<DashboardState>(DashboardState.Loading)
        private set

    private var lastLoaded: DashboardState.Loaded? = null

    suspend fun refresh() {
        if (lastLoaded == null) state = DashboardState.Loading
        state = try {
            DashboardState.Loaded(repo.load(), LocalTime.now().format(TIME)).also { lastLoaded = it }
        } catch (e: NotConfiguredException) {
            lastLoaded ?: DashboardState.NotConfigured
        } catch (e: Exception) {
            lastLoaded ?: DashboardState.Error(e.message ?: "ошибка загрузки")
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
