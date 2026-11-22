package dev.valkhes.mealplanner.domain.journal

/** When in the day a meal was eaten. [code] is stored in events: stable forever. */
enum class MealSlot(
    val code: String,
) {
    LUNCH("lunch"),
    DINNER("dinner"),
    OTHER("other"),
    ;

    companion object {
        fun fromCode(code: String): MealSlot = entries.firstOrNull { it.code == code } ?: OTHER
    }
}
