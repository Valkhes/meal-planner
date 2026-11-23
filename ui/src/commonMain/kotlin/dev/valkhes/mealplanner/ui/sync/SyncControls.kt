package dev.valkhes.mealplanner.ui.sync

import kotlinx.coroutines.flow.StateFlow

/** Where the sync with the other phone stands, for the Settings screen. */
sealed interface SyncState {
    data object Idle : SyncState

    /** Showing [code] for the other phone to type. */
    data class Inviting(
        val code: String,
    ) : SyncState

    data object Joining : SyncState

    data object Syncing : SyncState

    data object Paired : SyncState

    data class Synced(
        val sent: Int,
        val received: Int,
    ) : SyncState

    data class Failed(
        val reason: SyncFailure,
    ) : SyncState
}

enum class SyncFailure {
    /** No phone of the household answered on this network: is the app open on the other phone? */
    NO_PHONE_FOUND,

    /** Wrong code, or a phone of another household. */
    NOT_AUTHENTICATED,

    /** The other phone runs another version of the sync: update both apps. */
    OTHER_VERSION,

    CONNECTION_LOST,
}

/** The platform's sync over the local network (README, "Sync"). The UI only starts things and shows [state]. */
interface SyncControls {
    val state: StateFlow<SyncState>
    val paired: StateFlow<Boolean>

    fun invite()

    fun join(code: String)

    fun syncNow()

    fun cancel()
}
