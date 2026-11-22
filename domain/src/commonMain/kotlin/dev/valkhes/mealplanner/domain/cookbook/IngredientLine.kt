package dev.valkhes.mealplanner.domain.cookbook

/**
 * Reads and writes one ingredient line, in English or French: "200 g flour", "2 c. à soupe d'huile",
 * "1/2 cup sugar", "3 eggs", "salt". The quantity is kept as written; only the split matters.
 */
object IngredientLine {
    fun parse(line: String): Ingredient? {
        val text = line.trim().trimStart('-', '*', '•', '·').trim()
        if (text.isEmpty()) return null
        val match = QUANTITY.find(text) ?: return Ingredient(text)
        val quantity =
            match.value
                .trim()
                .removeSuffix("de")
                .removeSuffix("d'")
                .removeSuffix("d’")
                .removeSuffix("of")
                .trim()
        val name = text.substring(match.range.last + 1).trim()
        return if (name.isEmpty()) Ingredient(text) else Ingredient(name, quantity)
    }

    fun format(ingredient: Ingredient): String = if (ingredient.quantity.isNullOrBlank()) ingredient.name else "${ingredient.quantity} ${ingredient.name}"

    private const val NUMBER = """(?:\d+(?:[.,]\d+)?(?:\s*/\s*\d+)?|[½¼¾⅓⅔]|\d+\s*[½¼¾⅓⅔])"""

    private val UNITS: List<String> =
        listOf(
            // French
            "c\\. ?à ?soupe",
            "c\\. ?à ?café",
            "cuillères? à soupe",
            "cuillères? à café",
            "cs",
            "cc",
            "càs",
            "càc",
            "pincées?",
            "gousses?",
            "tranches?",
            "sachets?",
            "boîtes?",
            "verres?",
            "tasses?",
            "brins?",
            "feuilles?",
            // English
            "tbsp",
            "tsp",
            "tablespoons?",
            "teaspoons?",
            "cups?",
            "pinch(?:es)?",
            "cloves?",
            "slices?",
            "cans?",
            "sprigs?",
            "leaves",
            "oz",
            "lbs?",
            "pounds?",
            // Metric, both languages
            "kg",
            "g",
            "mg",
            "l",
            "dl",
            "cl",
            "ml",
        )

    private val QUANTITY: Regex =
        Regex(
            "^$NUMBER\\s*(?:(?:${UNITS.joinToString("|")})\\b\\.?)?\\s*(?:de |d'|d’|of )?",
            RegexOption.IGNORE_CASE,
        )
}
