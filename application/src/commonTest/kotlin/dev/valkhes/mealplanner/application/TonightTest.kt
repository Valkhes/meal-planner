package dev.valkhes.mealplanner.application

import dev.valkhes.mealplanner.application.habits.Reason
import dev.valkhes.mealplanner.application.habits.TonightView
import dev.valkhes.mealplanner.application.server.CommandResult
import dev.valkhes.mealplanner.application.server.CreateDish
import dev.valkhes.mealplanner.application.server.DishRef
import dev.valkhes.mealplanner.application.server.LocalMealServer
import dev.valkhes.mealplanner.application.server.LogMeal
import dev.valkhes.mealplanner.application.server.RemoveAbsence
import dev.valkhes.mealplanner.application.server.SetAbsence
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class TonightTest {
    /** The clock says 2026-11-20. */
    private suspend fun LocalMealServer.ate(
        dish: String,
        vararg days: LocalDate,
    ) {
        for (d in days) send(LogMeal(DishRef.Named(dish), d, MealSlot.DINNER))
    }

    private fun nov(d: Int): LocalDate = LocalDate(2026, 11, d)

    private fun oct(d: Int): LocalDate = LocalDate(2026, 10, d)

    @Test
    fun the_most_overdue_dish_comes_first_with_its_reason_and_today_is_skipped() =
        runTest {
            val clock = FakeClock()
            val server = openServer(clock = clock)
            server.ate("Curry", oct(19), oct(29), nov(8)) // every 10 days, last 12 days ago
            server.ate("Pasta", nov(12), nov(15), nov(18)) // every 3 days, last 2 days ago
            server.ate("Soup", nov(20)) // eaten today
            val tonight = server.tonight(null)
            assertEquals(listOf("Curry", "Pasta"), tonight.suggestions.map { it.dish.name.text })
            assertEquals(Reason.Rhythm(daysSince = 12, intervalDays = 10), tonight.suggestions.first().reason)
            assertEquals(7, tonight.mealsLogged)
            assertTrue(tonight.learning)

            clock.advance(30.days)
            assertEquals(
                "Pasta",
                server
                    .tonight(null)
                    .suggestions
                    .first()
                    .dish.name.text,
            )
        }

    @Test
    fun a_dish_too_long_to_cook_is_left_out_and_one_without_a_time_stays() =
        runTest {
            val server = openServer()
            server.send(CreateDish("Lasagne", Recipe(minutes = 90)))
            server.send(CreateDish("Omelette", Recipe(minutes = 10)))
            server.ate("Lasagne", oct(1))
            server.ate("Omelette", oct(2))
            server.ate("Salad", oct(3))
            assertEquals(
                listOf("Lasagne", "Omelette", "Salad").sorted(),
                server
                    .tonight(null)
                    .suggestions
                    .map { it.dish.name.text }
                    .sorted(),
            )
            assertEquals(
                listOf("Omelette", "Salad").sorted(),
                server
                    .tonight(30)
                    .suggestions
                    .map { it.dish.name.text }
                    .sorted(),
            )
        }

    @Test
    fun without_a_rhythm_the_reason_is_how_often_and_how_long_ago() =
        runTest {
            val server = openServer()
            server.ate("Pizza", nov(10), nov(13))
            assertEquals(
                Reason.Frequent(count = 2, daysSince = 7),
                server
                    .tonight(null)
                    .suggestions
                    .single()
                    .reason,
            )
        }

    @Test
    fun the_break_in_ends_after_enough_meals() =
        runTest {
            val server = openServer()
            for (d in 1..TonightView.BREAK_IN_MEALS) server.ate("Dish $d", nov(d))
            assertFalse(server.tonight(null).learning)
            assertEquals(TonightView.SIZE, server.tonight(null).suggestions.size)
        }

    @Test
    fun days_away_do_not_make_a_dish_overdue() =
        runTest {
            val server = openServer()
            server.ate("Curry", oct(19), oct(29), nov(8))
            server.send(SetAbsence(nov(9), nov(17)))
            // 12 days since, 9 of them away: 3 days at home out of 10.
            assertEquals(
                Reason.Rhythm(daysSince = 3, intervalDays = 10),
                server
                    .tonight(null)
                    .suggestions
                    .single()
                    .reason,
            )
        }

    @Test
    fun absences_are_listed_until_they_end_and_can_be_removed() =
        runTest {
            val server = openServer()
            server.send(SetAbsence(nov(1), nov(5)))
            val upcoming = server.send(SetAbsence(nov(24), nov(30))) as CommandResult.Done
            assertEquals(listOf(nov(24)), server.absences().map { it.days.start })
            assertTrue(server.isAway(nov(25)))
            assertEquals(CommandResult.Refused(DomainError.ABSENCE_INVALID), server.send(SetAbsence(nov(30), nov(24))))
            server.send(RemoveAbsence(upcoming.absenceId!!))
            assertFalse(server.isAway(nov(25)))
            assertEquals(CommandResult.Refused(DomainError.ABSENCE_UNKNOWN), server.send(RemoveAbsence(upcoming.absenceId!!)))
        }
}
