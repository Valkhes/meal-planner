package dev.valkhes.mealplanner.domain

import dev.valkhes.mealplanner.domain.habits.Habit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HabitTest {
    private fun day(d: Int): LocalDate = LocalDate(2026, 11, d)

    @Test
    fun no_interval_before_three_distinct_days() {
        val habit = Habit.of(listOf(day(1), day(10)))
        assertEquals(2, habit.count)
        assertEquals(day(10), habit.last)
        assertNull(habit.typicalIntervalDays)
    }

    @Test
    fun the_interval_is_the_median_gap() {
        // Gaps 9, 10, 30: one long holiday does not drag the rhythm.
        val habit = Habit.of(listOf(day(1), day(10), day(20), LocalDate(2026, 12, 20)))
        assertEquals(10, habit.typicalIntervalDays)
    }

    @Test
    fun lunch_and_dinner_on_one_day_count_twice_but_are_one_day_of_rhythm() {
        val habit = Habit.of(listOf(day(1), day(1), day(5), day(9)))
        assertEquals(4, habit.count)
        assertEquals(4, habit.typicalIntervalDays)
    }

    @Test
    fun due_ratio_compares_time_since_last_with_the_rhythm() {
        val habit = Habit.of(listOf(day(1), day(11), day(21)))
        assertEquals(1.2, habit.dueRatio(LocalDate(2026, 12, 3)))
        assertNull(Habit.of(listOf(day(1))).dueRatio(day(2)))
    }
}
