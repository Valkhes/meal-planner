package dev.valkhes.mealplanner.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.valkhes.mealplanner.application.server.LocalMealServer
import dev.valkhes.mealplanner.application.sync.SyncProtocolMismatch
import dev.valkhes.mealplanner.ui.sync.SyncControls
import dev.valkhes.mealplanner.ui.sync.SyncFailure
import dev.valkhes.mealplanner.ui.sync.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume

/**
 * The sync over the local network (README, "Sync"). While the app is in the foreground, the phone
 * listens on a random port and announces itself with mDNS (NSD). "Sync now" finds a phone of the
 * same household, both prove they hold the household key, and the server's sync runs over an
 * encrypted channel. Only private and link-local addresses are ever accepted or dialled
 * ([LocalNetwork]); this package is the only code allowed to open sockets (ArchitectureTest).
 */
class LanSync(
    context: Context,
    private val household: Household,
    private val server: suspend () -> LocalMealServer,
) : SyncControls {
    private val nsd: NsdManager = context.getSystemService(NsdManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFlow = MutableStateFlow<SyncState>(SyncState.Idle)
    private val pairedFlow = MutableStateFlow(household.isPaired())
    private var listening: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var ownServiceName: String? = null
    private var expiry: Job? = null

    @Volatile private var inviteCode: String? = null

    override val state: StateFlow<SyncState> = stateFlow
    override val paired: StateFlow<Boolean> = pairedFlow

    /** Listens and announces this phone while the app is in the foreground. */
    @Synchronized
    fun start() {
        if (listening != null) return
        val socket = ServerSocket(0)
        listening = socket
        scope.launch { acceptLoop(socket) }
        announce()
    }

    @Synchronized
    fun stop() {
        unannounce()
        listening?.close()
        listening = null
        cancel()
    }

    override fun invite() {
        val code = PairingCode.generate()
        inviteCode = code
        stateFlow.value = SyncState.Inviting(code)
        announce()
        expiry?.cancel()
        expiry =
            scope.launch {
                delay(INVITE_MILLIS)
                if (inviteCode == code) cancel()
            }
    }

    override fun cancel() {
        val wasInviting = inviteCode != null
        inviteCode = null
        expiry?.cancel()
        if (stateFlow.value is SyncState.Inviting) stateFlow.value = SyncState.Idle
        if (wasInviting) announce()
    }

    override fun join(code: String) {
        stateFlow.value = SyncState.Joining
        scope.launch {
            stateFlow.value =
                attempt {
                    val peer = find(pairing = true) ?: return@attempt SyncState.Failed(SyncFailure.NO_PHONE_FOUND)
                    dial(peer).use { socket ->
                        val frames = Frames(socket.getInputStream(), socket.getOutputStream())
                        frames.write(byteArrayOf(MODE_PAIR))
                        val channel = Handshake.pairing(frames, code, Handshake.INITIATOR)
                        val (householdId, key) = HouseholdInvitation.decode(channel.receiveBytes())
                        household.join(householdId, key)
                    }
                    pairedFlow.value = true
                    announce()
                    SyncState.Paired
                }
        }
    }

    override fun syncNow() {
        stateFlow.value = SyncState.Syncing
        scope.launch {
            stateFlow.value =
                attempt {
                    val peer = find(pairing = false) ?: return@attempt SyncState.Failed(SyncFailure.NO_PHONE_FOUND)
                    dial(peer).use { socket ->
                        val frames = Frames(socket.getInputStream(), socket.getOutputStream())
                        frames.write(byteArrayOf(MODE_SYNC))
                        val report = server().sync(Handshake.household(frames, household.key(), Handshake.INITIATOR))
                        SyncState.Synced(report.sent, report.received)
                    }
                }
        }
    }

    private suspend fun attempt(block: suspend () -> SyncState): SyncState =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: NotAuthenticated) {
            SyncState.Failed(SyncFailure.NOT_AUTHENTICATED)
        } catch (_: SyncProtocolMismatch) {
            SyncState.Failed(SyncFailure.OTHER_VERSION)
        } catch (_: Exception) {
            SyncState.Failed(SyncFailure.CONNECTION_LOST)
        }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client =
                try {
                    socket.accept()
                } catch (_: IOException) {
                    return
                }
            scope.launch { answer(client) }
        }
    }

    /** The other phone dialled us: sync, or receive an invitation's answer, if it proves itself. */
    private suspend fun answer(client: Socket) {
        client.use {
            if (!LocalNetwork.isLocal(it.inetAddress)) return
            it.soTimeout = TIMEOUT_MILLIS
            val frames = Frames(it.getInputStream(), it.getOutputStream())
            when (frames.read().singleOrNull()) {
                MODE_SYNC -> {
                    val result =
                        attempt {
                            val report = server().sync(Handshake.household(frames, household.key(), Handshake.RESPONDER))
                            SyncState.Synced(report.sent, report.received)
                        }
                    stateFlow.value = result
                }
                MODE_PAIR -> {
                    val code = inviteCode ?: return
                    val result =
                        attempt {
                            val channel = Handshake.pairing(frames, code, Handshake.RESPONDER)
                            channel.sendBytes(HouseholdInvitation.encode(household.current().householdId, household.key()))
                            household.markPaired()
                            pairedFlow.value = true
                            inviteCode = null
                            expiry?.cancel()
                            SyncState.Paired
                        }
                    stateFlow.value = result
                    announce()
                }
            }
        }
    }

    private fun dial(peer: InetSocketAddress): Socket {
        require(LocalNetwork.isLocal(peer.address)) { "not on the local network" }
        return Socket().apply {
            soTimeout = TIMEOUT_MILLIS
            connect(peer, TIMEOUT_MILLIS)
        }
    }

    /** Announces this phone: its household tag, and whether it waits for a pairing. */
    @Synchronized
    private fun announce() {
        val port = listening?.localPort ?: return
        unannounce()
        val info =
            NsdServiceInfo().apply {
                serviceName = "meal-planner-" + household.current().deviceId.takeLast(SERVICE_ID_CHARS)
                serviceType = SERVICE_TYPE
                setPort(port)
                setAttribute(ATTRIBUTE_HOUSEHOLD, householdTag())
                setAttribute(ATTRIBUTE_PAIRING, if (inviteCode != null) "1" else "0")
            }
        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    ownServiceName = info.serviceName
                }

                override fun onRegistrationFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) = Unit

                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

                override fun onUnregistrationFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) = Unit
            }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Synchronized
    private fun unannounce() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    /** A tag other phones of the household recognise, which says nothing about the key itself. */
    private fun householdTag(): String =
        Crypto
            .hmac(household.key(), "household tag".encodeToByteArray())
            .take(TAG_BYTES)
            .joinToString("") { "%02x".format(it) }

    /** Looks for another phone of the household (or one inviting, for [pairing]) on this network. */
    private suspend fun find(pairing: Boolean): InetSocketAddress? {
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (info.serviceName != ownServiceName) found.trySend(info)
                }

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    found.close()
                }

                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onServiceLost(info: NsdServiceInfo) = Unit

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit
            }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            return withTimeoutOrNull(DISCOVERY_MILLIS) {
                for (candidate in found) {
                    val resolved = resolve(candidate) ?: continue

                    @Suppress("DEPRECATION") // getHost is the only accessor below Android 14.
                    val host: InetAddress = resolved.host ?: continue
                    val attributes = resolved.attributes
                    val matches =
                        if (pairing) {
                            attributes[ATTRIBUTE_PAIRING]?.decodeToString() == "1"
                        } else {
                            attributes[ATTRIBUTE_HOUSEHOLD]?.decodeToString() == householdTag()
                        }
                    if (matches && LocalNetwork.isLocal(host)) return@withTimeoutOrNull InetSocketAddress(host, resolved.port)
                }
                null
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION") // resolveService is the only resolver below Android 14.
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) = continuation.resume(resolved)

                    override fun onResolveFailed(
                        failed: NsdServiceInfo,
                        errorCode: Int,
                    ) = continuation.resume(null)
                },
            )
        }

    private companion object {
        const val SERVICE_TYPE = "_mealplanner._tcp"
        const val ATTRIBUTE_HOUSEHOLD = "h"
        const val ATTRIBUTE_PAIRING = "p"
        const val MODE_SYNC: Byte = 'S'.code.toByte()
        const val MODE_PAIR: Byte = 'P'.code.toByte()
        const val TAG_BYTES = 8
        const val SERVICE_ID_CHARS = 8
        const val TIMEOUT_MILLIS = 15_000
        const val DISCOVERY_MILLIS = 8_000L
        const val INVITE_MILLIS = 5 * 60_000L
    }
}

/** What the inviting phone hands over once the code is proven: its household id and key. */
internal object HouseholdInvitation {
    fun encode(
        householdId: String,
        key: ByteArray,
    ): ByteArray = (householdId + "\n" + key.joinToString("") { "%02x".format(it) }).encodeToByteArray()

    fun decode(bytes: ByteArray): Pair<String, ByteArray> {
        val (id, hex) = bytes.decodeToString().split("\n", limit = 2)
        require(id.isNotBlank() && hex.length == 64) { "malformed invitation" }
        return id to hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
