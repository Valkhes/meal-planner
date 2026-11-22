package dev.valkhes.mealplanner.infrastructure.system

import dev.valkhes.mealplanner.application.shared.Clock
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The real clock: the only place that reads the system's time and time zone. */
object SystemClock : Clock {
    override fun now(): Instant =
        kotlin.time.Clock.System
            .now()

    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}
