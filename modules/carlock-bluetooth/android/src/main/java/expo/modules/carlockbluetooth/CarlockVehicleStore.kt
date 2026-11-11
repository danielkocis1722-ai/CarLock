package expo.modules.carlockbluetooth

import android.content.Context

/** One process-wide lock serializes background, NFC, notification, and JS mutations. */
internal object CarlockVehicleStore {
    const val STATE_KEY = "fleet_state_v2"
    private fun prefs(context: Context) = context.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)

    @Synchronized
    fun read(context: Context): VehicleFleet {
        val prefs = prefs(context)
        val raw = prefs.getString(STATE_KEY, null)
        if (raw != null) return FleetCodec.decode(raw)
        val fleet = FleetCodec.legacy(prefs.getString("selectedCarName", null), prefs.getString("selectedCarAddress", null),
            prefs.getBoolean("locked", true), prefs.getBoolean("connected", false),
            prefs.getString("lastEvent", "NONE") ?: "NONE", prefs.getLong("lastEventAt", 0L), prefs.getString("nfcTagId", null))
        prefs.edit().putString(STATE_KEY, FleetCodec.encode(fleet)).apply()
        CarlockLockConfirmation.cancelLegacyReminder(context)
        return fleet
    }

    private fun write(context: Context, before: VehicleFleet, next: VehicleFleet): VehicleFleet {
        if (before == next) return before
        val updated = next.copy(revision = before.revision + 1)
        prefs(context).edit().putString(STATE_KEY, FleetCodec.encode(updated)).apply()
        return updated
    }

    @Synchronized
    fun snapshot(context: Context) = FleetCodec.encode(read(context))

    @Synchronized
    fun importProfiles(context: Context, raw: String?): String {
        val before = read(context)
        return FleetCodec.encode(write(context, before, FleetCodec.importProfiles(before, raw)))
    }

    @Synchronized
    fun saveVehicle(context: Context, id: String, name: String, address: String, bluetoothName: String?): String {
        val before = read(context)
        val old = before.vehicle(id)
        val after = write(context, before, before.saveVehicle(id, name.trim(), address.trim().uppercase(), bluetoothName))
        if (old != null && !old.bluetoothAddress.equals(address.trim(), true)) {
            CarlockLockConfirmation.cancelReminder(context, id)
        } else if (old?.pending == true && old.name != name.trim()) {
            after.vehicle(id)?.let { CarlockReminderReceiver().show(context, it) }
        }
        return FleetCodec.encode(after)
    }

    @Synchronized
    fun deleteVehicle(context: Context, id: String): String {
        val before = read(context)
        val after = write(context, before, before.deleteVehicle(id))
        CarlockLockConfirmation.cancelReminder(context, id)
        return FleetCodec.encode(after)
    }

    @Synchronized
    fun bluetooth(context: Context, address: String, connected: Boolean) {
        val before = read(context)
        val next = before.bluetooth(address, connected, System.currentTimeMillis())
        if (before == next) return
        val after = write(context, before, next)
        val target = after.vehicle(after.currentVehicleId) ?: return
        if (connected) CarlockLockConfirmation.cancelReminder(context, target.id)
        else if (target.pending) {
            CarlockLockConfirmation.cancelLegacyReminder(context)
            CarlockReminderReceiver().show(context, target)
        }
    }

    @Synchronized
    fun adapterState(context: Context, enabled: Boolean) {
        val before = read(context)
        write(context, before, before.adapterState(enabled))
    }

    @Synchronized
    fun confirm(context: Context, id: String, disconnectAt: Long): Boolean {
        val before = read(context)
        val after = before.confirm(id, disconnectAt)
        if (before == after) return false
        write(context, before, after)
        CarlockLockConfirmation.cancelReminder(context, id)
        return true
    }

    @Synchronized
    fun confirmNfc(context: Context, tagId: String): Boolean {
        val before = read(context)
        val after = before.confirmNfc(tagId)
        if (before == after) return false
        val target = before.newestPending() ?: return false
        write(context, before, after)
        CarlockLockConfirmation.cancelReminder(context, target.id)
        return true
    }

    @Synchronized
    fun dismiss(context: Context, id: String, disconnectAt: Long) {
        val item = read(context).vehicle(id) ?: return
        if (item.pending && item.lastDisconnectAt == disconnectAt) CarlockLockConfirmation.cancelReminder(context, id)
    }

    @Synchronized
    fun addTag(context: Context, tagId: String): String {
        val before = read(context)
        return FleetCodec.encode(write(context, before, before.addTag(tagId)))
    }

    @Synchronized
    fun removeTag(context: Context, tagId: String): String {
        val before = read(context)
        return FleetCodec.encode(write(context, before, before.removeTag(tagId)))
    }
}
