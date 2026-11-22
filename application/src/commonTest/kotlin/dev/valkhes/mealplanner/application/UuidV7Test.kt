package dev.valkhes.mealplanner.application

import dev.valkhes.mealplanner.application.shared.UuidV7
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class UuidV7Test {
    @Test
    fun ids_are_version_7_and_sort_by_time() {
        val clock = FakeClock()
        val ids = UuidV7(clock, Random(42))
        val first = ids.newId()
        clock.advance(5.milliseconds)
        val second = ids.newId()
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(first), first)
        assertTrue(first < second)
        assertEquals(clock.now().toEpochMilliseconds(), second.replace("-", "").substring(0, 12).toLong(16))
    }
}
