package dev.valkhes.mealplanner.application.events

import dev.valkhes.mealplanner.application.cookbook.CookbookEvents
import dev.valkhes.mealplanner.application.journal.JournalEvents
import dev.valkhes.mealplanner.domain.shared.DomainEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** What a stored envelope means to this version of the app. */
sealed interface ReadEvent {
    val envelope: EventEnvelope

    data class Known(
        override val envelope: EventEnvelope,
        val event: DomainEvent,
    ) : ReadEvent

    /**
     * An event this app cannot read: a type or version from a newer app on the other phone. It is
     * kept in the store and synced on untouched, and the app ignores it until it is updated.
     */
    data class Unknown(
        override val envelope: EventEnvelope,
    ) : ReadEvent
}

/** Every event type of every context, and the conversion between events and envelopes. */
class EventSchema(
    types: List<EventType<*>>,
) {
    private val byName: Map<String, EventType<*>> = types.associateBy { it.name }
    private val byClass: Map<kotlin.reflect.KClass<*>, EventType<*>> = types.associateBy { it.eventClass }

    init {
        require(byName.size == types.size) { "two event types share a name" }
        require(byClass.size == types.size) { "two event types share a class" }
    }

    val types: Collection<EventType<*>> get() = byName.values

    fun typeOf(event: DomainEvent): EventType<*> =
        byClass[event::class] ?: throw IllegalArgumentException("${event::class.simpleName} is not registered in EventSchema")

    @Suppress("UNCHECKED_CAST")
    fun encode(event: DomainEvent): Pair<EventType<*>, String> {
        val type = typeOf(event) as EventType<DomainEvent>
        return type to type.encode(event).toString()
    }

    fun read(envelope: EventEnvelope): ReadEvent {
        val type = byName[envelope.type] ?: return ReadEvent.Unknown(envelope)
        if (envelope.version > type.version) return ReadEvent.Unknown(envelope)
        val payload = Json.parseToJsonElement(envelope.payload).jsonObject
        return ReadEvent.Known(envelope, decode(type, payload, envelope.version))
    }

    /** Decodes a payload of any released version: upcasts it, then reads it as the current version. */
    fun decode(
        type: EventType<*>,
        payload: JsonObject,
        version: Int,
    ): DomainEvent = type.decode(type.upcast(payload, version))

    companion object {
        val CURRENT: EventSchema = EventSchema(JournalEvents.types + CookbookEvents.types)
    }
}
