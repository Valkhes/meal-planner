package dev.valkhes.mealplanner.sync

import dev.valkhes.mealplanner.application.events.EventEnvelope
import dev.valkhes.mealplanner.application.events.EventStore
import dev.valkhes.mealplanner.application.server.DishRef
import dev.valkhes.mealplanner.application.server.LocalMealServer
import dev.valkhes.mealplanner.application.server.LogMeal
import dev.valkhes.mealplanner.application.shared.Device
import dev.valkhes.mealplanner.application.shared.IdGenerator
import dev.valkhes.mealplanner.application.sync.SyncReport
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.infrastructure.system.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.AEADBadTagException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncTransportTest {
    /** Two connected frame streams over a loopback socket pair. */
    private fun framePair(): Pair<Frames, Frames> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listening ->
            val dialled = Socket(InetAddress.getLoopbackAddress(), listening.localPort)
            val accepted = listening.accept()
            return Frames(dialled.getInputStream(), dialled.getOutputStream()) to Frames(accepted.getInputStream(), accepted.getOutputStream())
        }
    }

    /** Runs both ends of a handshake at once and returns both results, or the first failure. */
    private fun <T> both(
        a: () -> T,
        b: () -> T,
    ): Pair<Result<T>, Result<T>> {
        var second: Result<T>? = null
        val worker = thread { second = runCatching(b) }
        val first = runCatching(a)
        worker.join(10_000)
        return first to second!!
    }

    private val key = Crypto.randomBytes(32)

    @Test
    fun phones_with_the_household_key_talk_and_others_are_refused() {
        val (a, b) = framePair()
        val (mine, hers) = both({ Handshake.household(a, key, Handshake.INITIATOR) }, { Handshake.household(b, key, Handshake.RESPONDER) })
        mine.getOrThrow().sendBytes("hello".encodeToByteArray())
        assertEquals("hello", hers.getOrThrow().receiveBytes().decodeToString())
        hers.getOrThrow().sendBytes("back".encodeToByteArray())
        assertEquals("back", mine.getOrThrow().receiveBytes().decodeToString())

        val (c, d) = framePair()
        val stranger = Crypto.randomBytes(32)
        val (x, y) = both({ Handshake.household(c, key, Handshake.INITIATOR) }, { Handshake.household(d, stranger, Handshake.RESPONDER) })
        assertTrue(x.exceptionOrNull() is NotAuthenticated)
        assertTrue(y.exceptionOrNull() is NotAuthenticated)
    }

    @Test
    fun pairing_works_with_the_code_however_it_is_typed_and_fails_with_another() {
        val code = PairingCode.generate()
        assertTrue(Regex("^[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}$").matches(code), code)
        val (a, b) = framePair()
        val typed = code.lowercase().replace("-", " ")
        val (joiner, inviter) = both({ Handshake.pairing(a, typed, Handshake.INITIATOR) }, { Handshake.pairing(b, code, Handshake.RESPONDER) })
        inviter.getOrThrow().sendBytes(HouseholdInvitation.encode("household-9", key))
        val (household, received) = HouseholdInvitation.decode(joiner.getOrThrow().receiveBytes())
        assertEquals("household-9", household)
        assertContentEquals(key, received)

        val (c, d) = framePair()
        val (wrong, _) = both({ Handshake.pairing(c, "AAAAA-AAAAA", Handshake.INITIATOR) }, { Handshake.pairing(d, code, Handshake.RESPONDER) })
        assertTrue(wrong.exceptionOrNull() is NotAuthenticated)
    }

    @Test
    fun a_changed_frame_does_not_open() {
        val sessionKey = Crypto.randomBytes(32)
        val sealed = Crypto.seal(sessionKey, ByteArray(12), "secret".encodeToByteArray())
        sealed[0] = (sealed[0].toInt() xor 1).toByte()
        assertFailsWith<AEADBadTagException> { Crypto.open(sessionKey, ByteArray(12), sealed) }
    }

    @Test
    fun only_local_addresses_are_local() {
        for (local in listOf("192.168.1.20", "10.0.0.5", "172.16.3.4", "127.0.0.1", "169.254.10.1", "fe80::1", "fd12:3456::1", "::1")) {
            assertTrue(LocalNetwork.isLocal(InetAddress.getByName(local)), local)
        }
        for (remote in listOf("8.8.8.8", "100.64.0.1", "172.32.0.1", "2001:4860:4860::8888")) {
            assertFalse(LocalNetwork.isLocal(InetAddress.getByName(remote)), remote)
        }
    }

    @Test
    fun look_alike_letters_in_a_typed_code_are_forgiven() {
        assertEquals("01234ABCD1", PairingCode.normalize(" o1234-abcd l "))
    }

    private class MemoryStore : EventStore {
        private val events = mutableListOf<EventEnvelope>()

        override suspend fun append(envelopes: List<EventEnvelope>) {
            val known = events.mapTo(HashSet()) { it.id }
            events += envelopes.filter { known.add(it.id) }
        }

        override suspend fun all(): List<EventEnvelope> = events.sortedWith(compareBy({ it.recordedAt }, { it.id }))
    }

    private class Ids(
        private val prefix: String,
    ) : IdGenerator {
        private var next = 0

        override fun newId(): String = "$prefix-${++next}"
    }

    @Test
    fun two_servers_sync_over_an_encrypted_socket() =
        runBlocking {
            val mine = LocalMealServer.open(MemoryStore(), SystemClock, Ids("a"), device = { Device("h", "a") })
            val hers = LocalMealServer.open(MemoryStore(), SystemClock, Ids("b"), device = { Device("h", "b") })
            mine.send(LogMeal(DishRef.Named("Curry"), LocalDate(2026, 11, 20), MealSlot.DINNER))
            hers.send(LogMeal(DishRef.Named("Salad"), LocalDate(2026, 11, 20), MealSlot.DINNER))

            val listening = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val (myReport, herReport) =
                withContext(Dispatchers.IO) {
                    val answering =
                        async {
                            listening.accept().use { socket ->
                                val frames = Frames(socket.getInputStream(), socket.getOutputStream())
                                hers.sync(Handshake.household(frames, key, Handshake.RESPONDER))
                            }
                        }
                    val dialling =
                        Socket(InetAddress.getLoopbackAddress(), listening.localPort).use { socket ->
                            val frames = Frames(socket.getInputStream(), socket.getOutputStream())
                            mine.sync(Handshake.household(frames, key, Handshake.INITIATOR))
                        }
                    dialling to answering.await()
                }
            listening.close()
            assertEquals(SyncReport(sent = 2, received = 2), myReport)
            assertEquals(SyncReport(sent = 2, received = 2), herReport)
            assertEquals(listOf("Curry", "Salad"), mine.dishes().map { it.dish.name.text }.sorted())
            assertEquals(mine.dishes(), hers.dishes())
        }
}
