package dev.valkhes.mealplanner.application

import dev.valkhes.mealplanner.application.events.EventEnvelope
import dev.valkhes.mealplanner.application.events.EventStore
import dev.valkhes.mealplanner.application.server.LocalMealServer
import dev.valkhes.mealplanner.application.shared.Clock
import dev.valkhes.mealplanner.application.shared.Device
import dev.valkhes.mealplanner.application.shared.IdGenerator
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Instant

class InMemoryEventStore : EventStore {
    val envelopes: MutableList<EventEnvelope> = mutableListOf()

    override suspend fun append(envelopes: List<EventEnvelope>) {
        val known = this.envelopes.mapTo(HashSet()) { it.id }
        this.envelopes += envelopes.filter { known.add(it.id) }
    }

    override suspend fun all(): List<EventEnvelope> = envelopes.sortedWith(compareBy({ it.recordedAt }, { it.id }))
}

class FakeClock(
    var instant: Instant = LocalDateTime(2026, 11, 20, 21, 0).toInstant(PARIS),
) : Clock {
    override fun now(): Instant = instant

    override fun timeZone(): TimeZone = PARIS

    fun advance(by: Duration) {
        instant += by
    }

    companion object {
        val PARIS: TimeZone = TimeZone.of("Europe/Paris")
    }
}

/** Sortable, readable ids: id-0001, id-0002... */
class SequentialIds : IdGenerator {
    private var next = 0

    override fun newId(): String = "id-${(++next).toString().padStart(4, '0')}"
}

val DEVICE: Device = Device("household-1", "phone-1")

suspend fun openServer(
    store: InMemoryEventStore = InMemoryEventStore(),
    clock: FakeClock = FakeClock(),
    ids: IdGenerator = SequentialIds(),
): LocalMealServer = LocalMealServer.open(store, clock, ids, DEVICE)
