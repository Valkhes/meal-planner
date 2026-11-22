package dev.valkhes.mealplanner.application

import dev.valkhes.mealplanner.domain.cookbook.DishCreated
import dev.valkhes.mealplanner.domain.cookbook.DishName
import dev.valkhes.mealplanner.domain.cookbook.DishRenamed
import dev.valkhes.mealplanner.domain.cookbook.Ingredient
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.cookbook.RecipeEdited
import dev.valkhes.mealplanner.domain.journal.MealLogged
import dev.valkhes.mealplanner.domain.journal.MealRetracted
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainEvent
import dev.valkhes.mealplanner.domain.shared.MealId
import kotlinx.datetime.LocalDate

/**
 * One payload per released version of every event type, exactly as the app wrote it, with the event
 * it must still decode to. Append only: a golden is never edited or removed (skill new-event-version).
 */
object EventGoldens {
    data class Golden(
        val type: String,
        val version: Int,
        val payload: String,
        val expected: DomainEvent,
    )

    val ALL: List<Golden> =
        listOf(
            Golden(
                "MealLogged",
                1,
                """{"mealId":"0193a1b2-0000-7000-8000-000000000001","dishId":"0193a1b2-0000-7000-8000-000000000002","date":"2026-11-20","slot":"dinner"}""",
                MealLogged(
                    MealId("0193a1b2-0000-7000-8000-000000000001"),
                    DishId("0193a1b2-0000-7000-8000-000000000002"),
                    LocalDate(2026, 11, 20),
                    MealSlot.DINNER,
                ),
            ),
            Golden(
                "MealRetracted",
                1,
                """{"mealId":"0193a1b2-0000-7000-8000-000000000001"}""",
                MealRetracted(MealId("0193a1b2-0000-7000-8000-000000000001")),
            ),
            Golden(
                "DishCreated",
                1,
                """{"dishId":"0193a1b2-0000-7000-8000-000000000002","name":"Gratin dauphinois"}""",
                DishCreated(DishId("0193a1b2-0000-7000-8000-000000000002"), DishName.trusted("Gratin dauphinois")),
            ),
            Golden(
                "DishRenamed",
                1,
                """{"dishId":"0193a1b2-0000-7000-8000-000000000002","name":"Gratin"}""",
                DishRenamed(DishId("0193a1b2-0000-7000-8000-000000000002"), DishName.trusted("Gratin")),
            ),
            Golden(
                "RecipeEdited",
                1,
                """{"dishId":"0193a1b2-0000-7000-8000-000000000002","ingredients":[{"name":"potatoes","quantity":"1 kg"},{"name":"salt"}],"steps":["Slice.","Bake 1 h."],"minutes":75}""",
                RecipeEdited(
                    DishId("0193a1b2-0000-7000-8000-000000000002"),
                    Recipe(listOf(Ingredient("potatoes", "1 kg"), Ingredient("salt")), listOf("Slice.", "Bake 1 h."), minutes = 75),
                ),
            ),
        )
}
