package dev.valkhes.mealplanner.domain

import dev.valkhes.mealplanner.domain.journal.Journal
import dev.valkhes.mealplanner.domain.journal.MealLogged
import dev.valkhes.mealplanner.domain.journal.MealRetracted
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.Decision
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.domain.shared.MealId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalTest {
    private val curry = DishId("curry")
    private val day = LocalDate(2026, 11, 20)

    @Test
    fun the_same_dinner_logged_on_two_phones_is_one_occasion() {
        val journal =
            Journal.EMPTY
                .apply(MealLogged(MealId("a"), curry, day, MealSlot.DINNER))
                .apply(MealLogged(MealId("b"), curry, day, MealSlot.DINNER))
        assertEquals(2, journal.active.size)
        assertEquals(1, journal.occasions().size)
    }

    @Test
    fun lunch_and_dinner_of_the_same_dish_are_two_occasions() {
        val journal =
            Journal.EMPTY
                .apply(MealLogged(MealId("a"), curry, day, MealSlot.LUNCH))
                .apply(MealLogged(MealId("b"), curry, day, MealSlot.DINNER))
        assertEquals(2, journal.occasions().size)
    }

    @Test
    fun a_retracted_meal_no_longer_counts() {
        val journal =
            Journal.EMPTY
                .apply(MealLogged(MealId("a"), curry, day, MealSlot.DINNER))
                .apply(MealRetracted(MealId("a")))
        assertEquals(0, journal.occasions().size)
        assertEquals(Decision.Rejected(DomainError.MEAL_ALREADY_RETRACTED), journal.retract(MealId("a")))
        assertEquals(Decision.Rejected(DomainError.MEAL_UNKNOWN), journal.retract(MealId("nope")))
    }

    @Test
    fun a_retraction_that_arrives_before_its_meal_still_applies() {
        val journal =
            Journal.EMPTY
                .apply(MealRetracted(MealId("a")))
                .apply(MealLogged(MealId("a"), curry, day, MealSlot.DINNER))
        // The retraction of an unknown meal is ignored, so the meal counts: the log is replayed in
        // recorded order, which keeps a meal before its retraction on every phone.
        assertEquals(1, journal.occasions().size)
    }

    @Test
    fun active_meals_are_newest_first() {
        val journal =
            Journal.EMPTY
                .apply(MealLogged(MealId("a"), curry, day, MealSlot.LUNCH))
                .apply(MealLogged(MealId("b"), curry, day, MealSlot.DINNER))
                .apply(MealLogged(MealId("c"), curry, LocalDate(2026, 11, 18), MealSlot.DINNER))
        assertEquals(listOf("b", "a", "c"), journal.active.map { it.id.value })
    }
}
