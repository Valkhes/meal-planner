package dev.valkhes.mealplanner.domain

import dev.valkhes.mealplanner.domain.habits.Habit
import dev.valkhes.mealplanner.domain.planning.AbsenceId
import dev.valkhes.mealplanner.domain.planning.AbsenceRemoved
import dev.valkhes.mealplanner.domain.planning.AbsenceSet
import dev.valkhes.mealplanner.domain.planning.Planning
import dev.valkhes.mealplanner.domain.shared.DateRange
import dev.valkhes.mealplanner.domain.shared.Decision
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.domain.shared.daysWithin
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanningTest {
    private fun day(d: Int): LocalDate = LocalDate(2026, 11, d)

    private val trip = AbsenceId("trip")

    @Test
    fun an_absence_covers_its_days_both_ends_included() {
        val planning = Planning.EMPTY.apply(AbsenceSet(trip, DateRange(day(3), day(10))))
        assertTrue(planning.isAway(day(3)))
        assertTrue(planning.isAway(day(10)))
        assertFalse(planning.isAway(day(11)))
    }

    @Test
    fun backwards_or_endless_absences_are_refused() {
        assertEquals(Decision.Rejected(DomainError.ABSENCE_INVALID), Planning.EMPTY.setAbsence(trip, DateRange(day(10), day(3))))
        assertEquals(Decision.Rejected(DomainError.ABSENCE_INVALID), Planning.EMPTY.setAbsence(trip, DateRange(day(1), LocalDate(2028, 1, 1))))
    }

    @Test
    fun removing_an_absence() {
        val planning = Planning.EMPTY.apply(AbsenceSet(trip, DateRange(day(3), day(10))))
        assertEquals(Decision.Accepted(listOf(AbsenceRemoved(trip))), planning.remove(trip))
        assertEquals(Decision.Rejected(DomainError.ABSENCE_UNKNOWN), Planning.EMPTY.remove(trip))
        assertFalse(planning.apply(AbsenceRemoved(trip)).isAway(day(5)))
    }

    @Test
    fun days_within_counts_each_day_once() {
        val ranges = listOf(DateRange(day(3), day(10)), DateRange(day(8), day(12)))
        assertEquals(10, ranges.daysWithin(day(1), day(20)))
        assertEquals(3, ranges.daysWithin(day(9), day(12)))
        assertEquals(0, ranges.daysWithin(day(12), day(20)))
    }

    @Test
    fun days_away_do_not_stretch_a_rhythm() {
        // Every 10 days, with a week away between the 2nd and 3rd time.
        val away = listOf(DateRange(day(15), day(21)))
        val habit = Habit.of(listOf(day(1), day(11), day(28)), away)
        assertEquals(10, habit.typicalIntervalDays)
        // Last eaten on the 28th; today the 8th of December is 10 days later at home.
        assertEquals(1.0, habit.dueRatio(LocalDate(2026, 12, 8), away))
        assertEquals(5, Habit.of(listOf(day(10)), away).daysSince(day(22), away))
    }
}
