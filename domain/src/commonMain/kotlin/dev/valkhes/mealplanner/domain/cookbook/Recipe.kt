package dev.valkhes.mealplanner.domain.cookbook

/** One ingredient line: "200 g" of "flour". The quantity stays free text: units are the cook's. */
data class Ingredient(
    val name: String,
    val quantity: String? = null,
)

/** The household's own recipe for a dish. Every part is optional: a dish can be logged long before it has one. */
data class Recipe(
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val minutes: Int? = null,
    val notes: String? = null,
) {
    val isEmpty: Boolean get() = ingredients.isEmpty() && steps.isEmpty() && minutes == null && notes.isNullOrBlank()

    /** Bounds that keep a recipe sane whatever produced it: a person, a paste or an interpreter. */
    fun isValid(): Boolean =
        ingredients.size <= MAX_INGREDIENTS &&
            steps.size <= MAX_STEPS &&
            ingredients.all { it.name.isNotBlank() && it.name.length <= MAX_LINE } &&
            steps.all { it.isNotBlank() && it.length <= MAX_STEP } &&
            (minutes == null || minutes in 1..MAX_MINUTES) &&
            (notes == null || notes.length <= MAX_NOTES)

    companion object {
        const val MAX_INGREDIENTS: Int = 100
        const val MAX_STEPS: Int = 100
        const val MAX_LINE: Int = 200
        const val MAX_STEP: Int = 2000
        const val MAX_MINUTES: Int = 24 * 60
        const val MAX_NOTES: Int = 4000
    }
}
