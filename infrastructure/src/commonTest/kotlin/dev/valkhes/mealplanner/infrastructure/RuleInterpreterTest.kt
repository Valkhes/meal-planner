package dev.valkhes.mealplanner.infrastructure

import dev.valkhes.mealplanner.application.assistant.InterpretContext
import dev.valkhes.mealplanner.application.assistant.Interpretation
import dev.valkhes.mealplanner.domain.cookbook.Dish
import dev.valkhes.mealplanner.domain.cookbook.DishName
import dev.valkhes.mealplanner.domain.cookbook.Ingredient
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.infrastructure.assistant.RuleInterpreter
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RuleInterpreterTest {
    private val today = LocalDate(2026, 11, 20)
    private val curry = Dish(DishId("curry"), DishName.trusted("Green curry"))
    private val gratin = Dish(DishId("gratin"), DishName.trusted("Gratin dauphinois"))
    private val context = InterpretContext(listOf(curry, gratin), today, hour = 21)
    private val interpreter = RuleInterpreter()

    private suspend fun meal(
        text: String,
        ctx: InterpretContext = context,
    ): Interpretation.Meal = assertIs<Interpretation.Meal>(interpreter.interpret(text, ctx), text)

    @Test
    fun english_meals() =
        runTest {
            assertEquals(Interpretation.Meal("Green curry", curry.id, today, MealSlot.DINNER), meal("We had a green curry tonight"))
            assertEquals(Interpretation.Meal("Pasta carbonara", null, LocalDate(2026, 11, 19), MealSlot.LUNCH), meal("pasta carbonara yesterday at lunch"))
            assertEquals(Interpretation.Meal("Pizza", null, LocalDate(2026, 11, 19), MealSlot.DINNER), meal("pizza last night"))
        }

    @Test
    fun french_meals() =
        runTest {
            assertEquals(Interpretation.Meal("Gratin dauphinois", gratin.id, today, MealSlot.DINNER), meal("On a mangé un gratin dauphinois ce soir"))
            assertEquals(Interpretation.Meal("Pâtes au pesto", null, LocalDate(2026, 11, 19), MealSlot.LUNCH), meal("hier midi des pâtes au pesto"))
            assertEquals(Interpretation.Meal("Soupe", null, LocalDate(2026, 11, 18), MealSlot.DINNER), meal("avant-hier soir une soupe"))
        }

    @Test
    fun accents_and_case_do_not_matter_to_find_a_dish() =
        runTest {
            assertEquals(gratin.id, meal("GRATIN DAUPHINOIS").dishId)
            assertEquals(curry.id, meal("restes de green curry").dishId)
        }

    @Test
    fun without_a_time_the_hour_decides_the_slot() =
        runTest {
            assertEquals(MealSlot.LUNCH, meal("salade", context.copy(hour = 13)).slot)
            assertEquals(MealSlot.DINNER, meal("salade", context.copy(hour = 20)).slot)
            assertEquals(MealSlot.DINNER, meal("salade hier").slot)
        }

    @Test
    fun nothing_left_is_not_a_meal() =
        runTest {
            assertIs<Interpretation.NotUnderstood>(interpreter.interpret("ce soir", context))
            assertIs<Interpretation.NotUnderstood>(interpreter.interpret("   ", context))
        }

    @Test
    fun a_french_recipe() =
        runTest {
            val text =
                """
                Gratin dauphinois
                Pour 4 personnes
                Préparation : 20 min
                Cuisson : 1 h
                Ingrédients
                1 kg de pommes de terre
                50 cl de crème
                2 gousses d'ail
                sel
                Préparation
                1. Éplucher et couper les pommes de terre en rondelles.
                2. Frotter le plat avec l'ail.
                3. Cuire 1 h à 160 °C.
                """.trimIndent()
            val recipe = assertIs<Interpretation.NewRecipe>(interpreter.interpret(text, context))
            assertEquals("Gratin dauphinois", recipe.name)
            assertEquals(gratin.id, recipe.dishId)
            assertEquals(
                listOf(Ingredient("pommes de terre", "1 kg"), Ingredient("crème", "50 cl"), Ingredient("ail", "2 gousses"), Ingredient("sel")),
                recipe.recipe.ingredients,
            )
            assertEquals(3, recipe.recipe.steps.size)
            assertEquals("Frotter le plat avec l'ail.", recipe.recipe.steps[1])
            assertEquals(80, recipe.recipe.minutes)
        }

    @Test
    fun an_english_recipe_without_headers() =
        runTest {
            val text =
                """
                Pancakes
                200 g flour
                2 eggs
                300 ml milk
                Whisk everything together until smooth and rest for ten minutes.
                Cook each pancake for about a minute per side in a hot pan.
                Total time: 30 min
                """.trimIndent()
            val recipe = assertIs<Interpretation.NewRecipe>(interpreter.interpret(text, context))
            assertEquals("Pancakes", recipe.name)
            assertEquals(null, recipe.dishId)
            assertEquals(3, recipe.recipe.ingredients.size)
            assertEquals(2, recipe.recipe.steps.size)
            assertEquals(30, recipe.recipe.minutes)
        }
}
