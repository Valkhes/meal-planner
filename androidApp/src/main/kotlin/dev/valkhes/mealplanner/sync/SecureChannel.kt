package dev.valkhes.mealplanner.sync

import dev.valkhes.mealplanner.application.sync.SyncChannel
import dev.valkhes.mealplanner.application.sync.SyncCodec
import dev.valkhes.mealplanner.application.sync.SyncMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/** Length-prefixed frames over a byte stream, with a size limit so a peer cannot exhaust memory. */
internal class Frames(
    input: InputStream,
    output: OutputStream,
) {
    private val input = DataInputStream(input.buffered())
    private val output = DataOutputStream(output.buffered())

    fun write(bytes: ByteArray) {
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    fun read(): ByteArray {
        val size = input.readInt()
        require(size in 0..MAX_FRAME) { "frame of $size bytes refused" }
        return ByteArray(size).also { input.readFully(it) }
    }

    private companion object {
        const val MAX_FRAME: Int = 32 * 1024 * 1024
    }
}

/**
 * Frames sealed with AES-GCM under a session key. Each direction has its own nonce space (a
 * direction byte, then a counter), so no nonce is ever reused and a replayed or reordered frame
 * fails to open.
 */
internal class SecureChannel(
    private val frames: Frames,
    private val key: ByteArray,
    private val sendDirection: Int,
) : SyncChannel {
    private var sent = 0L
    private var received = 0L

    fun sendBytes(plain: ByteArray) = frames.write(Crypto.seal(key, nonce(sendDirection, sent++), plain))

    fun receiveBytes(): ByteArray = Crypto.open(key, nonce(3 - sendDirection, received++), frames.read())

    override suspend fun send(message: SyncMessage) = withContext(Dispatchers.IO) { sendBytes(SyncCodec.encode(message).encodeToByteArray()) }

    override suspend fun receive(): SyncMessage = withContext(Dispatchers.IO) { SyncCodec.decode(receiveBytes().decodeToString()) }

    private fun nonce(
        direction: Int,
        counter: Long,
    ): ByteArray =
        ByteBuffer
            .allocate(12)
            .putInt(direction)
            .putLong(counter)
            .array()
}
