package dev.valkhes.mealplanner.domain

import dev.valkhes.mealplanner.domain.cookbook.Cookbook
import dev.valkhes.mealplanner.domain.cookbook.DishCreated
import dev.valkhes.mealplanner.domain.cookbook.DishName
import dev.valkhes.mealplanner.domain.cookbook.DishRenamed
import dev.valkhes.mealplanner.domain.cookbook.Ingredient
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.cookbook.RecipeEdited
import dev.valkhes.mealplanner.domain.shared.Decision
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CookbookTest {
    private val curry = DishId("curry")

    private fun name(text: String): DishName = DishName.parse(text).first!!

    private fun withCurry(): Cookbook = Cookbook.EMPTY.apply(DishCreated(curry, name("Green curry")))

    @Test
    fun names_are_trimmed_and_bounded() {
        assertEquals("Green curry", name("  Green   curry ").text)
        assertEquals(DomainError.DISH_NAME_EMPTY, DishName.parse("   ").second)
        assertEquals(DomainError.DISH_NAME_TOO_LONG, DishName.parse("x".repeat(81)).second)
    }

    @Test
    fun the_same_name_with_other_case_or_accents_is_taken() {
        val decision = withCurry().create(DishId("other"), name("GREEN CURRY"))
        assertEquals(Decision.Rejected(DomainError.DISH_NAME_TAKEN), decision)
        assertEquals(
            curry,
            Cookbook.EMPTY
                .apply(DishCreated(curry, name("Crème brûlée")))
                .findByName(name("creme brulee"))
                ?.id,
        )
    }

    @Test
    fun renaming_an_unknown_dish_is_refused() {
        assertEquals(Decision.Rejected(DomainError.DISH_UNKNOWN), Cookbook.EMPTY.rename(curry, name("Curry")))
    }

    @Test
    fun renaming_to_the_same_name_records_nothing() {
        assertEquals(Decision.Accepted(emptyList<DishRenamed>()), withCurry().rename(curry, name("Green curry")))
    }

    @Test
    fun rename_and_recipe_edits_fold_in() {
        val recipe = Recipe(listOf(Ingredient("coconut milk", "400 ml")), listOf("Simmer."), minutes = 30)
        val book =
            withCurry()
                .apply(DishRenamed(curry, name("Thai curry")))
                .apply(RecipeEdited(curry, recipe))
        assertEquals("Thai curry", book[curry]?.name?.text)
        assertEquals(recipe, book[curry]?.recipe)
    }

    @Test
    fun an_invalid_recipe_is_refused() {
        val decision = withCurry().editRecipe(curry, Recipe(minutes = 0))
        assertEquals(Decision.Rejected(DomainError.RECIPE_INVALID), decision)
    }

    @Test
    fun an_edit_for_a_dish_not_seen_yet_changes_nothing() {
        assertNull(Cookbook.EMPTY.apply(RecipeEdited(curry, Recipe(minutes = 10)))[curry])
    }
}
