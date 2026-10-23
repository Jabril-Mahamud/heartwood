package dev.easonhuang.heartwood.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StepStatsTest {

    @Test
    fun periodTotalsOnlyCountsDaysFromStart() {
        val today = LocalDate.of(2026, 10, 23)
        val daily = (0L..4L).map { today.minusDays(4 - it) to 1000f * (it + 1) } // 19th..23rd
        val totals = periodTotals(daily, LocalDate.of(2026, 10, 21))
        assertEquals(12_000f, totals.total)
        assertEquals(4_000f, totals.average)
    }

    @Test
    fun periodTotalsSkipsUnreadableHistory() {
        val today = LocalDate.of(2026, 10, 23)
        val daily = listOf(today.minusDays(2) to 0f, today.minusDays(1) to 2000f, today to 0f)
        assertEquals(PeriodTotals(1000f, 2000f), periodTotals(daily, today.withDayOfYear(1)))
    }

    @Test
    fun weekStartCanFallInLastYear() {
        // Fri 1 Jan 2027: a Monday-start week began on 28 Dec 2026.
        assertEquals(LocalDate.of(2026, 12, 28), weekStartOf(LocalDate.of(2027, 1, 1), DayOfWeek.MONDAY))
    }

    @Test
    fun weekStartOnItsOwnStartDayIsToday() {
        val sunday = LocalDate.of(2026, 10, 25)
        assertEquals(sunday, weekStartOf(sunday, DayOfWeek.SUNDAY))
    }
}
