package dev.easonhuang.heartwood.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Population averages, not this user. Stride from HEIGHT (≈ 0.415 × height) and kcal from WEIGHT
// would be closer if those permissions are granted.
const val STRIDE_METERS = 0.762f
const val KCAL_PER_STEP = 0.04f
const val STEPS_PER_MINUTE = 100f

data class PeriodTotals(val average: Float, val total: Float)

/**
 * Daily average and total of [daily] from [from] onwards. Days before the first recorded steps are
 * skipped: without history access Health Connect only returns ~30 days, and those zeros would sink
 * a year's average. Zeros after that count, so a week matches the Today card's average.
 * Genuine 0-step days before the first recorded day are skipped too; check
 * PERMISSION_READ_HISTORY if that matters.
 */
fun periodTotals(daily: List<Pair<LocalDate, Float>>, from: LocalDate): PeriodTotals {
    val days = daily.dropWhile { it.second == 0f }.filter { !it.first.isBefore(from) }.map { it.second }
    return PeriodTotals(if (days.isEmpty()) 0f else days.average().toFloat(), days.sum())
}

/** The first day of the calendar week containing [today]. */
fun weekStartOf(today: LocalDate, start: DayOfWeek): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(start))
