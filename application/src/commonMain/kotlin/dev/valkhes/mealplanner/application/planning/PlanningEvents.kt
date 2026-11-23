package dev.valkhes.mealplanner.application.planning

import dev.valkhes.mealplanner.application.events.EventType
import dev.valkhes.mealplanner.application.events.json
import dev.valkhes.mealplanner.application.events.string
import dev.valkhes.mealplanner.domain.planning.AbsenceId
import dev.valkhes.mealplanner.domain.planning.AbsenceRemoved
import dev.valkhes.mealplanner.domain.planning.AbsenceSet
import dev.valkhes.mealplanner.domain.shared.DateRange
import kotlinx.datetime.LocalDate

/** The stored schema of the planning's events. Released versions are frozen: see EventType. */
object PlanningEvents {
    val absenceSet: EventType<AbsenceSet> =
        EventType(
            name = "AbsenceSet",
            eventClass = AbsenceSet::class,
            version = 1,
            encode = { json("absenceId" to it.absenceId.value, "from" to it.days.start.toString(), "to" to it.days.endInclusive.toString()) },
            decode = { AbsenceSet(AbsenceId(it.string("absenceId")), DateRange(LocalDate.parse(it.string("from")), LocalDate.parse(it.string("to")))) },
        )

    val absenceRemoved: EventType<AbsenceRemoved> =
        EventType(
            name = "AbsenceRemoved",
            eventClass = AbsenceRemoved::class,
            version = 1,
            encode = { json("absenceId" to it.absenceId.value) },
            decode = { AbsenceRemoved(AbsenceId(it.string("absenceId"))) },
        )

    val types: List<EventType<*>> = listOf(absenceSet, absenceRemoved)
}
