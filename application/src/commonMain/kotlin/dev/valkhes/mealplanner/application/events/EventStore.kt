package dev.valkhes.mealplanner.application.events

/** Where envelopes are kept: SQLite on the phone today, maybe an online store one day. */
interface EventStore {
    /** Stores envelopes not seen yet and ignores ids already stored, so a sync can send the same event twice. */
    suspend fun append(envelopes: List<EventEnvelope>)

    /** Every envelope, in recorded order (recordedAt, then id). */
    suspend fun all(): List<EventEnvelope>
}
