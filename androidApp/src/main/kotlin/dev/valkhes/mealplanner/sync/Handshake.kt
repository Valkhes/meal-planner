package dev.valkhes.mealplanner.sync

/** The other phone could not prove it holds the household key, or the pairing code. */
class NotAuthenticated : Exception("the other phone could not prove it is part of this household")

/**
 * How two phones prove to each other who they are before any data moves. Both sides contribute a
 * fresh nonce, prove knowledge of the shared secret with an HMAC bound to both nonces and to their
 * role, and derive a session key from it: a recorded session cannot be replayed, and nothing secret
 * is ever sent in the clear.
 */
internal object Handshake {
    const val INITIATOR: Int = 1
    const val RESPONDER: Int = 2

    /** Sync between phones of the same household: the shared secret is the household key. */
    fun household(
        frames: Frames,
        key: ByteArray,
        role: Int,
    ): SecureChannel = authenticate(frames, key, role, "sync")

    /**
     * Pairing: the inviting phone shows a code, the other types it. The code is stretched with
     * PBKDF2 and a salt from the inviter, then used like a household key for this one exchange.
     */
    fun pairing(
        frames: Frames,
        code: String,
        role: Int,
    ): SecureChannel {
        val salt =
            if (role == INITIATOR) {
                frames.read().also { require(it.size == NONCE_BYTES) }
            } else {
                Crypto.randomBytes(NONCE_BYTES).also { frames.write(it) }
            }
        return authenticate(frames, Crypto.pbkdf2(PairingCode.normalize(code), salt), role, "pairing")
    }

    private fun authenticate(
        frames: Frames,
        secret: ByteArray,
        role: Int,
        purpose: String,
    ): SecureChannel {
        val mine = Crypto.randomBytes(NONCE_BYTES)
        frames.write(mine)
        val theirs = frames.read()
        if (theirs.size != NONCE_BYTES) throw NotAuthenticated()
        val (initiatorNonce, responderNonce) = if (role == INITIATOR) mine to theirs else theirs to mine

        fun proof(of: Int) = Crypto.hmac(secret, purpose.encodeToByteArray(), byteArrayOf(of.toByte()), initiatorNonce, responderNonce)
        frames.write(proof(role))
        if (!Crypto.sameBytes(frames.read(), proof(3 - role))) throw NotAuthenticated()
        val session = Crypto.hkdf(secret, initiatorNonce + responderNonce, "meal-planner $purpose session")
        return SecureChannel(frames, session, role)
    }

    private const val NONCE_BYTES: Int = 16
}

/** Ten characters a person reads on one phone and types on the other: about 50 bits. */
internal object PairingCode {
    /** Crockford base 32: no I, L, O or U, so nothing reads as something else. */
    private const val ALPHABET: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH: Int = 10

    fun generate(): String {
        val bytes = Crypto.randomBytes(LENGTH)
        val code = bytes.map { ALPHABET[(it.toInt() and 0xFF) % ALPHABET.length] }.joinToString("")
        return code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2)
    }

    /** What the user typed, in the form the key is derived from: upper case, no separators, look-alikes mapped. */
    fun normalize(typed: String): String =
        typed
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
}
