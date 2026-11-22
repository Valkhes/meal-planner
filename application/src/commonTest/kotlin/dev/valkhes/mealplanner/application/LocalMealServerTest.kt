package dev.valkhes.mealplanner.application

import dev.valkhes.mealplanner.application.server.CommandResult
import dev.valkhes.mealplanner.application.server.CreateDish
import dev.valkhes.mealplanner.application.server.DishRef
import dev.valkhes.mealplanner.application.server.EditRecipe
import dev.valkhes.mealplanner.application.server.LogMeal
import dev.valkhes.mealplanner.application.server.RenameDish
import dev.valkhes.mealplanner.application.server.RetractMeal
import dev.valkhes.mealplanner.domain.cookbook.Ingredient
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class LocalMealServerTest {
    private val day = LocalDate(2026, 11, 20)

    @Test
    fun logging_a_new_name_creates_the_dish_then_reuses_it() =
        runTest {
            val store = InMemoryEventStore()
            val server = openServer(store)
            val first = server.send(LogMeal(DishRef.Named("Green curry"), day, MealSlot.DINNER)) as CommandResult.Done
            val second = server.send(LogMeal(DishRef.Named(" green CURRY "), LocalDate(2026, 11, 25), MealSlot.DINNER)) as CommandResult.Done
            assertEquals(first.dishId, second.dishId)
            assertEquals(listOf("DishCreated", "MealLogged", "MealLogged"), store.envelopes.map { it.type })
            val dish = server.dishes().single()
            assertEquals("Green curry", dish.dish.name.text)
            assertEquals(2, dish.habit.count)
            assertEquals(2L, server.revision.value)
        }

    @Test
    fun refused_commands_record_nothing() =
        runTest {
            val store = InMemoryEventStore()
            val server = openServer(store)
            assertEquals(CommandResult.Refused(DomainError.DISH_NAME_EMPTY), server.send(LogMeal(DishRef.Named("  "), day, MealSlot.DINNER)))
            assertEquals(CommandResult.Refused(DomainError.DISH_UNKNOWN), server.send(LogMeal(DishRef.Existing(DishId("nope")), day, MealSlot.DINNER)))
            assertEquals(0, store.envelopes.size)
            assertEquals(0L, server.revision.value)
        }

    @Test
    fun the_state_is_the_same_after_a_restart() =
        runTest {
            val store = InMemoryEventStore()
            val clock = FakeClock()
            val server = openServer(store, clock)
            val created = server.send(CreateDish("Lasagne", Recipe(listOf(Ingredient("pasta sheets", "12")), minutes = 60))) as CommandResult.Done
            clock.advance(1.minutes)
            server.send(RenameDish(created.dishId!!, "Lasagne bolognese"))
            clock.advance(1.minutes)
            val logged = server.send(LogMeal(DishRef.Existing(created.dishId!!), day, MealSlot.DINNER)) as CommandResult.Done
            server.send(RetractMeal(logged.mealId!!))
            server.send(LogMeal(DishRef.Existing(created.dishId!!), day, MealSlot.LUNCH))

            val reopened = openServer(store, clock)
            assertEquals(server.dishes(), reopened.dishes())
            assertEquals(server.recentMeals(10), reopened.recentMeals(10))
            assertEquals(
                "Lasagne bolognese",
                reopened
                    .dish(created.dishId!!)
                    ?.dish
                    ?.name
                    ?.text,
            )
            assertEquals(
                60,
                reopened
                    .dish(created.dishId!!)
                    ?.dish
                    ?.recipe
                    ?.minutes,
            )
        }

    @Test
    fun a_meal_occurs_on_its_day_and_is_recorded_now() =
        runTest {
            val store = InMemoryEventStore()
            val clock = FakeClock()
            val server = openServer(store, clock)
            server.send(LogMeal(DishRef.Named("Soup"), LocalDate(2026, 11, 18), MealSlot.LUNCH))
            val meal = store.envelopes.single { it.type == "MealLogged" }
            assertEquals("2026-11-18T11:30:00Z", meal.occurredAt.toString())
            assertEquals(clock.now(), meal.recordedAt)
            assertEquals("Europe/Paris", meal.timeZone)
            assertEquals(DEVICE.householdId, meal.householdId)
        }

    @Test
    fun editing_a_recipe_to_the_same_recipe_records_nothing() =
        runTest {
            val store = InMemoryEventStore()
            val server = openServer(store)
            val recipe = Recipe(steps = listOf("Boil."))
            val id = (server.send(CreateDish("Pasta", recipe)) as CommandResult.Done).dishId!!
            val before = store.envelopes.size
            assertIs<CommandResult.Done>(server.send(EditRecipe(id, recipe)))
            assertEquals(before, store.envelopes.size)
        }

    @Test
    fun suggestions_put_the_most_overdue_dish_first_and_skip_today() =
        runTest {
            val clock = FakeClock()
            val server = openServer(clock = clock)
            val today = LocalDate(2026, 11, 20)
            // Curry every 10 days, last 12 days ago: overdue. Pasta every 3 days, last 2 days ago: not yet.
            for (d in listOf(19, 29)) server.send(LogMeal(DishRef.Named("Curry"), LocalDate(2026, 10, d), MealSlot.DINNER))
            server.send(LogMeal(DishRef.Named("Curry"), LocalDate(2026, 11, 8), MealSlot.DINNER))
            for (d in listOf(12, 15, 18)) server.send(LogMeal(DishRef.Named("Pasta"), LocalDate(2026, 11, d), MealSlot.DINNER))
            server.send(LogMeal(DishRef.Named("Soup"), today, MealSlot.LUNCH))
            assertEquals(listOf("Curry", "Pasta"), server.suggestions(5).map { it.dish.name.text })
            clock.advance(30.days)
            assertEquals(
                "Pasta",
                server
                    .suggestions(5)
                    .first()
                    .dish.name.text,
            )
        }

    @Test
    fun an_unknown_dish_has_no_detail() =
        runTest {
            assertNull(openServer().dish(DishId("nope")))
        }
}
