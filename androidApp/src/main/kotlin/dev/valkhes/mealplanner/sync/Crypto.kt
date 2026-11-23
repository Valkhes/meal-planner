package dev.valkhes.mealplanner.sync

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The few primitives the sync needs, all from the platform: HMAC-SHA256, HKDF, PBKDF2, AES-GCM. */
internal object Crypto {
    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }

    /** HMAC-SHA256 of the parts, each prefixed with its length so no two inputs collide. */
    fun hmac(
        key: ByteArray,
        vararg parts: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        for (part in parts) {
            mac.update(ByteBuffer.allocate(4).putInt(part.size).array())
            mac.update(part)
        }
        return mac.doFinal()
    }

    /** HKDF-SHA256 (RFC 5869), one 32-byte block. */
    fun hkdf(
        key: ByteArray,
        salt: ByteArray,
        info: String,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(key)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info.encodeToByteArray())
        mac.update(1)
        return mac.doFinal()
    }

    /** Stretches a typed pairing code so that trying every code offline is out of reach. */
    fun pbkdf2(
        code: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
    ): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun sameBytes(
        a: ByteArray,
        b: ByteArray,
    ): Boolean = MessageDigest.isEqual(a, b)

    fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plain: ByteArray,
    ): ByteArray = gcm(Cipher.ENCRYPT_MODE, key, nonce).doFinal(plain)

    /** Throws if the bytes were not sealed with this key and nonce, or were changed on the way. */
    fun open(
        key: ByteArray,
        nonce: ByteArray,
        sealed: ByteArray,
    ): ByteArray = gcm(Cipher.DECRYPT_MODE, key, nonce).doFinal(sealed)

    private fun gcm(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
    ): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)) }

    const val PBKDF2_ITERATIONS: Int = 200_000
}
