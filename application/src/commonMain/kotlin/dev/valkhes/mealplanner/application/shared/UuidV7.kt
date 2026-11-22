package dev.valkhes.mealplanner.application.shared

import kotlin.random.Random

/**
 * UUIDv7 (RFC 9562): 48 bits of Unix milliseconds, then random bits. Ids sort by creation time and
 * never clash between phones, whichever store they meet in.
 */
class UuidV7(
    private val clock: Clock,
    private val random: Random,
) : IdGenerator {
    override fun newId(): String {
        val millis = clock.now().toEpochMilliseconds()
        val bytes = random.nextBytes(16)
        for (i in 0 until 6) bytes[i] = (millis ushr (8 * (5 - i))).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // version 7
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // RFC 4122 variant
        val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
