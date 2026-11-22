package dev.valkhes.mealplanner.application.journal

import dev.valkhes.mealplanner.application.events.EventType
import dev.valkhes.mealplanner.application.events.json
import dev.valkhes.mealplanner.application.events.string
import dev.valkhes.mealplanner.domain.journal.MealLogged
import dev.valkhes.mealplanner.domain.journal.MealRetracted
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.MealId
import kotlinx.datetime.LocalDate

/** The stored schema of the journal's events. Released versions are frozen: see EventType. */
object JournalEvents {
    val mealLogged: EventType<MealLogged> =
        EventType(
            name = "MealLogged",
            eventClass = MealLogged::class,
            version = 1,
            encode = { json("mealId" to it.mealId.value, "dishId" to it.dishId.value, "date" to it.date.toString(), "slot" to it.slot.code) },
            decode = {
                MealLogged(MealId(it.string("mealId")), DishId(it.string("dishId")), LocalDate.parse(it.string("date")), MealSlot.fromCode(it.string("slot")))
            },
        )

    val mealRetracted: EventType<MealRetracted> =
        EventType(
            name = "MealRetracted",
            eventClass = MealRetracted::class,
            version = 1,
            encode = { json("mealId" to it.mealId.value) },
            decode = { MealRetracted(MealId(it.string("mealId"))) },
        )

    val types: List<EventType<*>> = listOf(mealLogged, mealRetracted)
}
