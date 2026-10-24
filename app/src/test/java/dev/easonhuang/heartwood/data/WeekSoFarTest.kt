package dev.easonhuang.heartwood.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekSoFarTest {

    private val daily = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f)
    private val friday = LocalDate.of(2026, 10, 23)

    @Test
    fun mondayStartOnFridayKeepsFiveDays() {
        val week = weekSoFar(daily, DayOfWeek.MONDAY, friday)
        assertEquals(listOf(3f, 4f, 5f, 6f, 7f), week.map { it.second })
        assertEquals(DayOfWeek.MONDAY, week.first().first.dayOfWeek)
        assertEquals(friday, week.last().first)
    }

    @Test
    fun sundayStartOnFridayKeepsSixDays() {
        val week = weekSoFar(daily, DayOfWeek.SUNDAY, friday)
        assertEquals(DayOfWeek.SUNDAY, week.first().first.dayOfWeek)
        assertEquals(6, week.size)
    }

    @Test
    fun firstDayOfWeekIsJustToday() {
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(listOf(monday to 7f), weekSoFar(daily, DayOfWeek.MONDAY, monday))
    }

    @Test
    fun shortSeriesKeepsDatesEndingToday() {
        val week = weekSoFar(listOf(1f, 2f, 3f), DayOfWeek.MONDAY, friday)
        assertEquals(listOf(friday.minusDays(2), friday.minusDays(1), friday), week.map { it.first })
    }
}
