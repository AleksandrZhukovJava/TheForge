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

/** Where the widget/popover get their data. Swapped for a live Jira/GitLab repo next. */
interface DashboardRepository {
    suspend fun load(): DashboardData
}

/** Placeholder data through the real refresh pipeline (so cadence + states are exercised now). */
class SampleDashboardRepository : DashboardRepository {
    override suspend fun load(): DashboardData {
        delay(400)
        return DashboardData(jiraRows, mrRows, pipelineRows)
    }
}

/** Holds the current [DashboardState]; polled by the app on an interval and by manual refresh. */
class DashboardHolder(private val repo: DashboardRepository) {
    var state by mutableStateOf<DashboardState>(DashboardState.Loading)
        private set

    suspend fun refresh() {
        state = DashboardState.Loading
        state = try {
            DashboardState.Loaded(repo.load(), LocalTime.now().format(TIME))
        } catch (e: Exception) {
            DashboardState.Error(e.message ?: "ошибка загрузки")
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
