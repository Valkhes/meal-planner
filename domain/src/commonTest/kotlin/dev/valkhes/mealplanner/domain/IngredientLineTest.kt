package dev.valkhes.mealplanner.domain

import dev.valkhes.mealplanner.domain.cookbook.Ingredient
import dev.valkhes.mealplanner.domain.cookbook.IngredientLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IngredientLineTest {
    @Test
    fun english_lines() {
        assertEquals(Ingredient("flour", "200 g"), IngredientLine.parse("200 g flour"))
        assertEquals(Ingredient("sugar", "1/2 cup"), IngredientLine.parse("1/2 cup of sugar"))
        assertEquals(Ingredient("eggs", "3"), IngredientLine.parse("- 3 eggs"))
        assertEquals(Ingredient("olive oil", "2 tbsp"), IngredientLine.parse("2 tbsp olive oil"))
        assertEquals(Ingredient("salt"), IngredientLine.parse("salt"))
    }

    @Test
    fun french_lines() {
        assertEquals(Ingredient("huile d'olive", "2 c. à soupe"), IngredientLine.parse("2 c. à soupe d'huile d'olive"))
        assertEquals(Ingredient("lait de coco", "40 cl"), IngredientLine.parse("40 cl de lait de coco"))
        assertEquals(Ingredient("ail", "2 gousses"), IngredientLine.parse("• 2 gousses d'ail"))
        assertEquals(Ingredient("beurre", "1,5 kg"), IngredientLine.parse("1,5 kg de beurre"))
        assertEquals(Ingredient("oignon", "½"), IngredientLine.parse("½ oignon"))
    }

    @Test
    fun a_unit_is_only_a_unit_as_a_whole_word() {
        assertEquals(Ingredient("gros oignons", "2"), IngredientLine.parse("2 gros oignons"))
        assertEquals(Ingredient("lemons", "2"), IngredientLine.parse("2 lemons"))
    }

    @Test
    fun blank_lines_are_not_ingredients_and_formatting_round_trips() {
        assertNull(IngredientLine.parse("   "))
        assertEquals("200 g flour", IngredientLine.format(Ingredient("flour", "200 g")))
        assertEquals("salt", IngredientLine.format(Ingredient("salt")))
    }
}
