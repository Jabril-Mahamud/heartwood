package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

private val Context.dashboardDataStore by preferencesDataStore(name = "heartwood_dashboard")

/**
 * The user's Today-dashboard arrangement: the order cards appear in and which are hidden.
 *
 * [order] always contains every [Metric] exactly once, so a metric added in a future release
 * simply appears at the end (visible). [visible] is [order] minus [hidden] — the cards the
 * dashboard actually renders, in order.
 */
data class DashboardLayout(
    val order: List<Metric>,
    val hidden: Set<Metric>,
) {
    val visible: List<Metric> get() = order.filter { it !in hidden }

    companion object {
        val DEFAULT = DashboardLayout(order = Metric.entries.toList(), hidden = emptySet())
    }
}

/**
 * Default ring color per weekday (Monday first), in rainbow order: red, orange, yellow, green, blue,
 * indigo, violet. Every hue is a multiple of 30 so each default is also one of Settings' swatches.
 */
val defaultWeekdayColors = listOf(0f, 30f, 60f, 120f, 210f, 240f, 270f)
    .map { Color.hsl(hue = it, saturation = 0.70f, lightness = 0.60f) }

/** How the Today week rings are drawn: the day the week starts on and each weekday's color. */
data class WeekStyle(val start: DayOfWeek, val colors: List<Color>) {
    fun color(day: DayOfWeek): Color = colors[day.value - 1]

    companion object {
        val DEFAULT = WeekStyle(DayOfWeek.MONDAY, defaultWeekdayColors)
    }
}

/**
 * The current calendar week's days so far, oldest first, cut from [daily] totals ending [today].
 * Days after today aren't included, so on the first day of the week there's exactly one.
 */
fun weekSoFar(daily: List<Float>, start: DayOfWeek, today: LocalDate): List<Pair<LocalDate, Float>> {
    val elapsed = (today.dayOfWeek.value - start.value + 7) % 7 + 1
    val days = daily.takeLast(elapsed)
    return days.mapIndexed { i, v -> today.minusDays((days.size - 1 - i).toLong()) to v }
}

/**
 * Persists [DashboardLayout] in DataStore. Order is stored as a comma-separated list of metric
 * keys and the hidden set as a string set of keys; both are reconciled against the current
 * [Metric] catalog on read so stale or missing keys can never corrupt the dashboard.
 */
class DashboardPreferences(private val context: Context) {

    private val orderKey = stringPreferencesKey("card_order")
    private val hiddenKey = stringSetPreferencesKey("hidden_cards")

    val layout: Flow<DashboardLayout> = context.dashboardDataStore.data.map { prefs ->
        val stored = prefs[orderKey]
            ?.split(',')
            ?.mapNotNull { Metric.fromKey(it) }
            ?: emptyList()
        // Keep the stored order, then append any catalog metric it doesn't mention (e.g. a
        // metric introduced after the user last customised) so nothing silently disappears.
        val order = (stored + Metric.entries.filter { it !in stored })
        val hidden = prefs[hiddenKey].orEmpty().mapNotNull { Metric.fromKey(it) }.toSet()
        DashboardLayout(order, hidden)
    }

    private val goalRingsKey = booleanPreferencesKey("goal_rings")
    private val weekStartKey = intPreferencesKey("week_start")
    private fun dayColorKey(day: DayOfWeek) = intPreferencesKey("day_color_${day.value}")

    val weekStyle: Flow<WeekStyle> = context.dashboardDataStore.data.map { prefs ->
        WeekStyle(
            start = prefs[weekStartKey]?.let(DayOfWeek::of) ?: DayOfWeek.MONDAY,
            colors = DayOfWeek.entries.map { day ->
                prefs[dayColorKey(day)]?.let(::Color) ?: defaultWeekdayColors[day.value - 1]
            },
        )
    }

    /** Whether Today draws the step goal ring and week rings. Steps always has a goal, so this is the opt-out. */
    val goalRings: Flow<Boolean> = context.dashboardDataStore.data.map { prefs -> prefs[goalRingsKey] ?: true }

    suspend fun setGoalRings(show: Boolean) {
        context.dashboardDataStore.edit { prefs -> prefs[goalRingsKey] = show }
    }

    suspend fun setWeekStart(day: DayOfWeek) {
        context.dashboardDataStore.edit { prefs -> prefs[weekStartKey] = day.value }
    }

    /** Sets [day]'s ring color, or back to the default when [color] is null. */
    suspend fun setDayColor(day: DayOfWeek, color: Color?) {
        context.dashboardDataStore.edit { prefs ->
            if (color == null) prefs.remove(dayColorKey(day)) else prefs[dayColorKey(day)] = color.toArgb()
        }
    }

    suspend fun save(order: List<Metric>, hidden: Set<Metric>) {
        context.dashboardDataStore.edit { prefs ->
            prefs[orderKey] = order.joinToString(",") { it.key }
            prefs[hiddenKey] = hidden.map { it.key }.toSet()
        }
    }
}
