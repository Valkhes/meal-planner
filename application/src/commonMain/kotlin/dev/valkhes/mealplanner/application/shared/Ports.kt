package dev.valkhes.mealplanner.application.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The only source of time. Nothing in the domain or application layers reads the system clock. */
interface Clock {
    fun now(): Instant

    fun timeZone(): TimeZone
}

fun Clock.today(): LocalDate = now().toLocalDateTime(timeZone()).date

fun Clock.hour(): Int = now().toLocalDateTime(timeZone()).hour

/** New ids, generated on the phone (README, "Ready for an online database"). */
fun interface IdGenerator {
    fun newId(): String
}

/** Which household and which phone write the events. Created once, at first launch. */
data class Device(
    val householdId: String,
    val deviceId: String,
)
