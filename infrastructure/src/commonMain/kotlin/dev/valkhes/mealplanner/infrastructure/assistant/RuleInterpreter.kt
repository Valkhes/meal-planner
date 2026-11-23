package dev.valkhes.mealplanner.infrastructure.assistant

import dev.valkhes.mealplanner.application.assistant.InterpretContext
import dev.valkhes.mealplanner.application.assistant.Interpretation
import dev.valkhes.mealplanner.application.assistant.Interpreter
import dev.valkhes.mealplanner.domain.cookbook.Dish
import dev.valkhes.mealplanner.domain.cookbook.DishName
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.textKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

/**
 * The interpreter without AI, always there (README, "The input box"). It understands English and
 * French: a short sentence is days away ("on part du 3 au 10") or a meal ("curry tonight",
 * "hier midi des pâtes"); three lines or more are a pasted recipe.
 */
class RuleInterpreter : Interpreter {
    private val recipes = RecipeTextParser()
    private val absences = AbsenceTextParser()

    override suspend fun interpret(
        text: String,
        context: InterpretContext,
    ): Interpretation {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Interpretation.NotUnderstood
        if (lines.size >= RECIPE_MIN_LINES) {
            val parsed = recipes.parse(lines) ?: return Interpretation.NotUnderstood
            val dishId = parsed.title?.let { title -> matchDish(title, context.dishes, exactOnly = true) }
            return Interpretation.NewRecipe(parsed.title, dishId, parsed.recipe)
        }
        val sentence = lines.joinToString(" ")
        absences.parse(sentence, context.today)?.let { (from, to) -> return Interpretation.Absence(from, to) }
        return meal(sentence, context)
    }

    private fun meal(
        sentence: String,
        context: InterpretContext,
    ): Interpretation {
        val words = sentence.split(Regex("[\\s,.!?;:]+")).filter { it.isNotEmpty() }
        val keys = words.map { textKey(it) }
        val dropped = BooleanArray(words.size)
        var dayOffset: Int? = null
        var slot: MealSlot? = null

        for (phrase in TIME_PHRASES) {
            forEachMatch(keys, dropped, phrase.words) {
                if (phrase.dayOffset != null && dayOffset == null) dayOffset = phrase.dayOffset
                if (phrase.slot != null && slot == null) slot = phrase.slot
            }
        }
        for (filler in FILLERS) forEachMatch(keys, dropped, filler) {}

        val remaining = words.indices.filterNot { dropped[it] }.toMutableList()
        while (remaining.isNotEmpty() && keys[remaining.first()] in ARTICLES) remaining.removeAt(0)
        val name =
            remaining
                .joinToString(" ") { words[it] }
                .removePrefix("l'")
                .removePrefix("d'")
                .removePrefix("l’")
                .removePrefix("d’")
                .trim()
        if (DishName.parse(name).first == null) return Interpretation.NotUnderstood

        val existing = matchDish(name, context.dishes, exactOnly = false)
        val dishName =
            existing?.let { id ->
                context.dishes
                    .first { it.id == id }
                    .name.text
            } ?: name.replaceFirstChar { it.uppercase() }
        val date = context.today.minus(DatePeriod(days = dayOffset ?: 0))
        val mealSlot = slot ?: if (dayOffset == null && context.hour < AFTERNOON) MealSlot.LUNCH else MealSlot.DINNER
        return Interpretation.Meal(dishName, existing, date, mealSlot)
    }

    /** Marks every occurrence of [phrase] (keys of consecutive words) as dropped and runs [found] once per match. */
    private fun forEachMatch(
        keys: List<String>,
        dropped: BooleanArray,
        phrase: List<String>,
        found: () -> Unit,
    ) {
        var i = 0
        while (i + phrase.size <= keys.size) {
            val matches = phrase.indices.all { !dropped[i + it] && keys[i + it] == phrase[it] }
            if (matches) {
                for (j in phrase.indices) dropped[i + j] = true
                found()
                i += phrase.size
            } else {
                i++
            }
        }
    }

    /** The dish with the same name; or, for a sentence, the longest known name it contains ("leftover curry" is "Curry"). */
    private fun matchDish(
        name: String,
        dishes: List<Dish>,
        exactOnly: Boolean,
    ): DishId? {
        val key = textKey(name)
        dishes.firstOrNull { it.name.key == key }?.let { return it.id }
        if (exactOnly) return null
        return dishes
            .filter { it.name.key.length >= MIN_PARTIAL && Regex("\\b${Regex.escape(it.name.key)}\\b").containsMatchIn(key) }
            .maxByOrNull { it.name.key.length }
            ?.id
    }

    private class TimePhrase(
        text: String,
        val dayOffset: Int?,
        val slot: MealSlot?,
    ) {
        val words: List<String> = text.split(" ")
    }

    private companion object {
        const val RECIPE_MIN_LINES: Int = 3
        const val AFTERNOON: Int = 16
        const val MIN_PARTIAL: Int = 4

        /** Longest first: "hier soir" must win over "hier". Keys are accent-free and lowercase. */
        val TIME_PHRASES: List<TimePhrase> =
            listOf(
                TimePhrase("the day before yesterday", 2, null),
                TimePhrase("avant hier soir", 2, MealSlot.DINNER),
                TimePhrase("avant hier midi", 2, MealSlot.LUNCH),
                TimePhrase("avant-hier soir", 2, MealSlot.DINNER),
                TimePhrase("avant-hier midi", 2, MealSlot.LUNCH),
                TimePhrase("avant hier", 2, null),
                TimePhrase("avant-hier", 2, null),
                TimePhrase("yesterday at lunch", 1, MealSlot.LUNCH),
                TimePhrase("yesterday for lunch", 1, MealSlot.LUNCH),
                TimePhrase("yesterday for dinner", 1, MealSlot.DINNER),
                TimePhrase("yesterday lunch", 1, MealSlot.LUNCH),
                TimePhrase("yesterday dinner", 1, MealSlot.DINNER),
                TimePhrase("last night", 1, MealSlot.DINNER),
                TimePhrase("hier soir", 1, MealSlot.DINNER),
                TimePhrase("hier midi", 1, MealSlot.LUNCH),
                TimePhrase("this evening", 0, MealSlot.DINNER),
                TimePhrase("at lunch", null, MealSlot.LUNCH),
                TimePhrase("for lunch", null, MealSlot.LUNCH),
                TimePhrase("for dinner", null, MealSlot.DINNER),
                TimePhrase("ce soir", 0, MealSlot.DINNER),
                TimePhrase("ce midi", 0, MealSlot.LUNCH),
                TimePhrase("a midi", null, MealSlot.LUNCH),
                TimePhrase("au dejeuner", null, MealSlot.LUNCH),
                TimePhrase("au diner", null, MealSlot.DINNER),
                TimePhrase("tonight", 0, MealSlot.DINNER),
                TimePhrase("yesterday", 1, null),
                TimePhrase("hier", 1, null),
                TimePhrase("today", 0, null),
                TimePhrase("aujourd'hui", 0, null),
                TimePhrase("midi", null, MealSlot.LUNCH),
                TimePhrase("soir", null, MealSlot.DINNER),
                TimePhrase("lunch", null, MealSlot.LUNCH),
                TimePhrase("dinner", null, MealSlot.DINNER),
            )

        /** Words around the dish name that say nothing about it. */
        val FILLERS: List<List<String>> =
            listOf(
                "on a mange",
                "nous avons mange",
                "j'ai mange",
                "on mange",
                "on s'est fait",
                "on a fait",
                "j'ai fait",
                "we ate",
                "we had",
                "we made",
                "i ate",
                "i had",
                "i made",
                "ate",
                "had",
                "mange",
            ).map { it.split(" ") }

        val ARTICLES: Set<String> = setOf("un", "une", "des", "du", "de", "la", "le", "les", "a", "an", "the", "some", "some")
    }
}
