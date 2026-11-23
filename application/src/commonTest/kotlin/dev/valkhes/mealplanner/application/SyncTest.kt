package dev.valkhes.mealplanner.application

import dev.valkhes.mealplanner.application.server.CommandResult
import dev.valkhes.mealplanner.application.server.CreateDish
import dev.valkhes.mealplanner.application.server.DishRef
import dev.valkhes.mealplanner.application.server.EditRecipe
import dev.valkhes.mealplanner.application.server.LocalMealServer
import dev.valkhes.mealplanner.application.server.LogMeal
import dev.valkhes.mealplanner.application.shared.Device
import dev.valkhes.mealplanner.application.shared.IdGenerator
import dev.valkhes.mealplanner.application.sync.SyncChannel
import dev.valkhes.mealplanner.application.sync.SyncCodec
import dev.valkhes.mealplanner.application.sync.SyncMessage
import dev.valkhes.mealplanner.application.sync.SyncProtocolMismatch
import dev.valkhes.mealplanner.application.sync.SyncReport
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.journal.MealSlot
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class SyncTest {
    private val day = LocalDate(2026, 11, 20)

    /** Each message goes through the wire format, as on a real transport. */
    private class Pipe(
        private val outgoing: Channel<String>,
        private val incoming: Channel<String>,
    ) : SyncChannel {
        override suspend fun send(message: SyncMessage) = outgoing.send(SyncCodec.encode(message))

        override suspend fun receive(): SyncMessage = SyncCodec.decode(incoming.receive())
    }

    private suspend fun syncBoth(
        a: LocalMealServer,
        b: LocalMealServer,
    ): Pair<SyncReport, SyncReport> {
        val ab = Channel<String>(Channel.UNLIMITED)
        val ba = Channel<String>(Channel.UNLIMITED)
        return coroutineScope {
            val first = async { a.sync(Pipe(ab, ba)) }
            val second = async { b.sync(Pipe(ba, ab)) }
            first.await() to second.await()
        }
    }

    /** Ids that never clash between the two phones, like UUIDv7 ones. */
    private class PrefixedIds(
        private val prefix: String,
    ) : IdGenerator {
        private var next = 0

        override fun newId(): String = "$prefix-${(++next).toString().padStart(4, '0')}"
    }

    private suspend fun phone(
        name: String,
        clock: FakeClock,
    ): Pair<LocalMealServer, InMemoryEventStore> {
        val store = InMemoryEventStore()
        return LocalMealServer.open(store, clock, PrefixedIds(name), Device("household-1", name)) to store
    }

    @Test
    fun both_phones_end_with_the_union_of_their_meals() =
        runTest {
            val clock = FakeClock()
            val (mine, myStore) = phone("a", clock)
            val (hers, herStore) = phone("b", clock)
            mine.send(LogMeal(DishRef.Named("Curry"), day, MealSlot.DINNER))
            hers.send(LogMeal(DishRef.Named("Soup"), LocalDate(2026, 11, 19), MealSlot.LUNCH))

            val (myReport, herReport) = syncBoth(mine, hers)
            assertEquals(SyncReport(sent = 2, received = 2), myReport)
            assertEquals(SyncReport(sent = 2, received = 2), herReport)
            assertEquals(myStore.all().map { it.id }, herStore.all().map { it.id })
            assertEquals(mine.dishes(), hers.dishes())
            assertEquals(2, mine.recentMeals(10).size)
        }

    @Test
    fun syncing_again_sends_nothing() =
        runTest {
            val clock = FakeClock()
            val (mine, _) = phone("a", clock)
            val (hers, _) = phone("b", clock)
            mine.send(LogMeal(DishRef.Named("Curry"), day, MealSlot.DINNER))
            syncBoth(mine, hers)
            assertEquals(SyncReport(0, 0) to SyncReport(0, 0), syncBoth(mine, hers))
        }

    @Test
    fun the_same_dinner_logged_on_both_phones_counts_once() =
        runTest {
            val clock = FakeClock()
            val (mine, _) = phone("a", clock)
            val (hers, _) = phone("b", clock)
            mine.send(LogMeal(DishRef.Named("Curry"), day, MealSlot.DINNER))
            syncBoth(mine, hers)
            hers.send(LogMeal(DishRef.Named("curry"), day, MealSlot.DINNER))
            syncBoth(mine, hers)
            assertEquals(
                1,
                mine
                    .dishes()
                    .single()
                    .habit.count,
            )
            assertEquals(
                1,
                hers
                    .dishes()
                    .single()
                    .habit.count,
            )
        }

    @Test
    fun the_latest_recipe_edit_wins_on_both_phones() =
        runTest {
            val clock = FakeClock()
            val (mine, _) = phone("a", clock)
            val (hers, _) = phone("b", clock)
            val id = (mine.send(CreateDish("Gratin")) as CommandResult.Done).dishId!!
            syncBoth(mine, hers)
            clock.advance(1.minutes)
            hers.send(EditRecipe(id, Recipe(minutes = 60)))
            clock.advance(1.minutes)
            mine.send(EditRecipe(id, Recipe(minutes = 75)))
            syncBoth(mine, hers)
            assertEquals(
                75,
                mine
                    .dish(id)
                    ?.dish
                    ?.recipe
                    ?.minutes,
            )
            assertEquals(
                75,
                hers
                    .dish(id)
                    ?.dish
                    ?.recipe
                    ?.minutes,
            )
        }

    @Test
    fun events_from_a_newer_app_are_passed_on_untouched() =
        runTest {
            val clock = FakeClock()
            val (mine, myStore) = phone("a", clock)
            val (hers, herStore) = phone("b", clock)
            mine.send(LogMeal(DishRef.Named("Curry"), day, MealSlot.DINNER))
            val future = myStore.envelopes.first().copy(id = "a-9999", type = "TasteNoted", payload = """{"liked":true}""")
            myStore.append(listOf(future))
            syncBoth(mine, hers)
            assertEquals(future, herStore.all().single { it.id == "a-9999" })
            assertEquals(
                1,
                hers
                    .dishes()
                    .single()
                    .habit.count,
            )
        }

    @Test
    fun another_protocol_is_refused() =
        runTest {
            val (mine, _) = phone("a", FakeClock())
            val stranger =
                object : SyncChannel {
                    override suspend fun send(message: SyncMessage) = Unit

                    override suspend fun receive(): SyncMessage = SyncMessage.Hello(protocol = 99, deviceId = "z", ids = emptySet())
                }
            assertEquals(99, assertFailsWith<SyncProtocolMismatch> { mine.sync(stranger) }.theirs)
        }
}
