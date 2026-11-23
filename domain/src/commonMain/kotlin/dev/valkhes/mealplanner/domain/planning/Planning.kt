package dev.valkhes.mealplanner.domain.planning

import dev.valkhes.mealplanner.domain.shared.DateRange
import dev.valkhes.mealplanner.domain.shared.Decision
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.domain.shared.DomainEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.jvm.JvmInline

@JvmInline
value class AbsenceId(
    val value: String,
)

/** Days the household is away: nothing is cooked, no reminder, and the rhythm does not count them. */
data class Absence(
    val id: AbsenceId,
    val days: DateRange,
)

sealed interface PlanningEvent : DomainEvent

data class AbsenceSet(
    val absenceId: AbsenceId,
    val days: DateRange,
) : PlanningEvent

data class AbsenceRemoved(
    val absenceId: AbsenceId,
) : PlanningEvent

/** The household's plans. Immutable: [apply] returns the next state. */
class Planning private constructor(
    private val absences: Map<AbsenceId, Absence>,
) {
    /** Every absence, earliest first. */
    val all: List<Absence> get() = absences.values.sortedWith(compareBy({ it.days.start }, { it.id.value }))

    val ranges: List<DateRange> get() = all.map { it.days }

    fun isAway(date: LocalDate): Boolean = absences.values.any { date in it.days }

    fun setAbsence(
        id: AbsenceId,
        days: DateRange,
    ): Decision<PlanningEvent> =
        when {
            days.start > days.endInclusive || days.start.daysUntil(days.endInclusive) >= MAX_DAYS -> Decision.Rejected(DomainError.ABSENCE_INVALID)
            absences[id]?.days == days -> Decision.Accepted(emptyList())
            else -> Decision.Accepted(listOf(AbsenceSet(id, days)))
        }

    fun remove(id: AbsenceId): Decision<PlanningEvent> =
        if (id in absences) Decision.Accepted(listOf(AbsenceRemoved(id))) else Decision.Rejected(DomainError.ABSENCE_UNKNOWN)

    fun apply(event: PlanningEvent): Planning =
        when (event) {
            is AbsenceSet -> Planning(absences + (event.absenceId to Absence(event.absenceId, event.days)))
            is AbsenceRemoved -> Planning(absences - event.absenceId)
        }

    companion object {
        val EMPTY: Planning = Planning(emptyMap())

        /** A year at most: a longer absence is a typing mistake. */
        const val MAX_DAYS: Int = 366
    }
}
