package dev.valkhes.mealplanner.application.events

import dev.valkhes.mealplanner.domain.shared.DomainEvent
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

/**
 * One kind of event and every version of its payload schema that was ever released.
 *
 * Rules (skill `new-event-version`): a released [version] is never edited; any change other than an
 * optional field with a default makes version N+1, with an upcaster from N; every version has a
 * golden payload in `EventGoldens`.
 */
class EventType<E : DomainEvent>(
    /** Stored in every envelope: stable forever. */
    val name: String,
    val eventClass: KClass<E>,
    /** The version this code writes. */
    val version: Int,
    /** upcasters[n] turns a version-n payload into version n+1. */
    val upcasters: Map<Int, (JsonObject) -> JsonObject> = emptyMap(),
    val encode: (E) -> JsonObject,
    val decode: (JsonObject) -> E,
) {
    init {
        require((1 until version).all { it in upcasters }) { "$name: every version below $version needs an upcaster" }
    }

    fun upcast(
        payload: JsonObject,
        from: Int,
    ): JsonObject {
        var current = payload
        for (v in from until version) current = upcasters.getValue(v)(current)
        return current
    }
}
