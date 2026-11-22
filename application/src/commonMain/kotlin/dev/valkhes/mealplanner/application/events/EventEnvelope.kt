package dev.valkhes.mealplanner.application.events

import kotlin.time.Instant

/**
 * How an event is stored, forever. This shape never changes: new needs go in [payload], whose own
 * schema is versioned per event [type] (README, "Data that survives iteration").
 */
data class EventEnvelope(
    /** UUIDv7, generated on the phone that recorded the event. */
    val id: String,
    val type: String,
    val version: Int,
    val householdId: String,
    val deviceId: String,
    /** When the fact happened, as the user says it (the meal's day, not when it was typed). */
    val occurredAt: Instant,
    /** The user's time zone when it was recorded, so the same data means the same thing anywhere. */
    val timeZone: String,
    val recordedAt: Instant,
    /** The event's content, JSON, in the schema of [version]. */
    val payload: String,
)
