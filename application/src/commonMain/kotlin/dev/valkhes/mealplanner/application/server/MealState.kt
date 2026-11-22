package dev.valkhes.mealplanner.application.server

import dev.valkhes.mealplanner.domain.cookbook.Cookbook
import dev.valkhes.mealplanner.domain.cookbook.CookbookEvent
import dev.valkhes.mealplanner.domain.journal.Journal
import dev.valkhes.mealplanner.domain.journal.JournalEvent
import dev.valkhes.mealplanner.domain.shared.DomainEvent

/**
 * The read state, rebuilt from the whole log at every start (a few thousand events take well under a
 * second). It is never saved, so its shape can change freely: no migration, ever.
 */
data class MealState(
    val cookbook: Cookbook,
    val journal: Journal,
) {
    fun apply(event: DomainEvent): MealState =
        when (event) {
            is CookbookEvent -> copy(cookbook = cookbook.apply(event))
            is JournalEvent -> copy(journal = journal.apply(event))
            else -> this
        }

    companion object {
        val EMPTY: MealState = MealState(Cookbook.EMPTY, Journal.EMPTY)
    }
}
