package dev.valkhes.mealplanner.application.assistant

import dev.valkhes.mealplanner.application.server.DishRef
import dev.valkhes.mealplanner.application.server.LogMeal
import dev.valkhes.mealplanner.domain.cookbook.Dish
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DishId
import kotlinx.datetime.LocalDate

/**
 * Turns what someone typed or pasted into proposals the user confirms. Never saves anything itself.
 * Implementations: rules today; Gemini Nano or a local Gemma later (README, "The input box").
 */
interface Interpreter {
    suspend fun interpret(
        text: String,
        context: InterpretContext,
    ): Interpretation
}

data class InterpretContext(
    val dishes: List<Dish>,
    val today: LocalDate,
    /** The hour now, 0-23: "we ate a curry" at 21:00 means tonight's dinner. */
    val hour: Int,
)

sealed interface Interpretation {
    /** A meal to log. [dishId] is set when the name matches a dish the household already has. */
    data class Meal(
        val dishName: String,
        val dishId: DishId?,
        val date: LocalDate,
        val slot: MealSlot,
    ) : Interpretation {
        fun toCommand(): LogMeal = LogMeal(if (dishId != null) DishRef.Existing(dishId) else DishRef.Named(dishName), date, slot)
    }

    /** A pasted recipe. [dishId] is set when its title matches an existing dish, whose recipe it would replace. */
    data class NewRecipe(
        val name: String?,
        val dishId: DishId?,
        val recipe: Recipe,
    ) : Interpretation

    data object NotUnderstood : Interpretation
}
