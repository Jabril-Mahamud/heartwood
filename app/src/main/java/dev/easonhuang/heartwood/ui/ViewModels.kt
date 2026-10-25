package dev.easonhuang.heartwood.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.heartwood.data.DashboardLayout
import dev.easonhuang.heartwood.data.DashboardPreferences
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricDetail
import dev.easonhuang.heartwood.data.MetricSummary
import dev.easonhuang.heartwood.data.WeekStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val manager: HealthConnectManager,
    private val prefs: DashboardPreferences,
    goalsRepo: GoalsRepository,
) : ViewModel() {
    // All summaries as read from Health Connect, in catalog order (null until first load).
    private val _summaries = MutableStateFlow<List<MetricSummary>?>(null)

    // Goals are joined here rather than at read time so editing one updates the cards live. With
    // rings switched off no goal is attached, so the steps card falls back to its sparkline.
    private val withGoals = combine(_summaries, goalsRepo.goals, prefs.goalRings) { all, goals, rings ->
        all?.map { it.copy(goal = if (rings) goals[it.metric] else null) }
    }

    /**
     * Summaries arranged for display: reordered to match the user's layout and with hidden
     * cards dropped. Stays null until the first read so the screen can show its loading state.
     */
    val summaries: StateFlow<List<MetricSummary>?> =
        combine(withGoals, prefs.layout) { all, layout ->
            if (all == null) return@combine null
            val byMetric = all.associateBy { it.metric }
            layout.visible.mapNotNull { byMetric[it] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every summary keyed by metric, hidden ones included, for the dashboard's edit mode. */
    val allSummaries: StateFlow<Map<Metric, MetricSummary>?> =
        withGoals.map { all -> all?.associateBy { it.metric } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Week start and per-day colors for the week rings card. */
    val weekStyle: StateFlow<WeekStyle> =
        prefs.weekStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekStyle.DEFAULT)

    /** The persisted card order and hidden set (null until DataStore's first read). */
    val layout: StateFlow<DashboardLayout?> =
        prefs.layout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveLayout(order: List<Metric>, hidden: Set<Metric>) {
        viewModelScope.launch { prefs.save(order, hidden) }
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _summaries.value = manager.readDashboard()
            _refreshing.value = false
        }
    }

    companion object {
        fun factory(manager: HealthConnectManager, prefs: DashboardPreferences, goalsRepo: GoalsRepository) = viewModelFactory {
            initializer { DashboardViewModel(manager, prefs, goalsRepo) }
        }
    }
}

class DetailViewModel(
    private val manager: HealthConnectManager,
    private val metric: Metric,
    private val prefs: DashboardPreferences,
) : ViewModel() {
    private val _detail = MutableStateFlow<MetricDetail?>(null)
    val detail = _detail.asStateFlow()

    init {
        viewModelScope.launch {
            val detail = manager.readDetail(metric)
            _detail.value = if (metric == Metric.STEPS) detail.copy(stats = detail.stats + stepPeriodStats()) else detail
        }
    }

    // Uses the week rings' first day so "this week" here matches the Today card.
    private suspend fun stepPeriodStats(): List<Pair<String, String>> = runCatching {
        manager.readStepPeriodStats(prefs.weekStyle.first().start)
    }.getOrDefault(emptyList())

    companion object {
        fun factory(manager: HealthConnectManager, metric: Metric, prefs: DashboardPreferences) = viewModelFactory {
            initializer { DetailViewModel(manager, metric, prefs) }
        }
    }
}
