package dev.valkhes.mealplanner.sync

import android.content.Context
import android.util.Base64
import dev.valkhes.mealplanner.application.shared.Device
import dev.valkhes.mealplanner.application.shared.DeviceIdentity
import dev.valkhes.mealplanner.application.shared.IdGenerator

/**
 * This phone's identity and its household's secret key, in the app's private storage (never backed
 * up: README, constraint 1). Every phone starts as its own household; pairing makes the joining
 * phone adopt the inviting phone's household id and key. The phone id never changes.
 */
class Household(
    context: Context,
    private val ids: IdGenerator,
) : DeviceIdentity {
    private val prefs = context.getSharedPreferences("household", Context.MODE_PRIVATE)

    override fun current(): Device = Device(householdId(), deviceId())

    /** 32 random bytes shared by the household's phones: it authenticates them and encrypts the sync. */
    fun key(): ByteArray {
        prefs.getString(KEY, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val key = Crypto.randomBytes(KEY_BYTES)
        prefs.edit().putString(KEY, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    /** True once this phone invited another or joined one. */
    fun isPaired(): Boolean = prefs.getBoolean(PAIRED, false)

    fun markPaired() {
        prefs.edit().putBoolean(PAIRED, true).apply()
    }

    /** Joins the other phone's household. Events already recorded keep the household they were written in. */
    fun join(
        householdId: String,
        key: ByteArray,
    ) {
        prefs
            .edit()
            .putString(HOUSEHOLD, householdId)
            .putString(KEY, Base64.encodeToString(key, Base64.NO_WRAP))
            .putBoolean(PAIRED, true)
            .apply()
    }

    private fun householdId(): String = prefs.getString(HOUSEHOLD, null) ?: ids.newId().also { prefs.edit().putString(HOUSEHOLD, it).apply() }

    private fun deviceId(): String = prefs.getString(DEVICE, null) ?: ids.newId().also { prefs.edit().putString(DEVICE, it).apply() }

    private companion object {
        const val HOUSEHOLD = "household_id"
        const val DEVICE = "device_id"
        const val KEY = "key"
        const val PAIRED = "paired"
        const val KEY_BYTES = 32
    }
}
