package expo.modules.carlockbluetooth

/** Pure state rules shared by every native entry point; no Android/React dependency. */
internal data class FleetVehicle(
    val id: String,
    val name: String,
    val bluetoothAddress: String,
    val bluetoothName: String? = null,
    val locked: Boolean = true,
    val connected: Boolean = false,
    val lastEvent: String = "NONE",
    val lastEventAt: Long = 0,
    val lastDisconnectAt: Long = 0
) {
    val pending: Boolean get() = !locked && !connected && lastEvent == "DISCONNECTED" && lastDisconnectAt > 0
    val state: String get() = if (locked) "LOCKED" else if (connected) "IN_CAR" else "UNCONFIRMED"
    fun reminder(): VehicleReminder? = if (pending) VehicleReminder(id, lastDisconnectAt, "Did you lock $name?") else null
}

internal data class VehicleReminder(val vehicleId: String, val disconnectAt: Long, val title: String) {
    val notificationTag get() = notificationTag(vehicleId)
    companion object { fun notificationTag(id: String) = "vehicle:$id" }
}

internal data class VehicleFleet(
    val vehicles: List<FleetVehicle> = emptyList(),
    val configuredNfcTags: List<String> = emptyList(),
    val currentVehicleId: String? = null,
    val legacyProfilesImported: Boolean = false,
    val revision: Long = 0
) {
    fun vehicle(id: String?) = vehicles.find { it.id == id }
    fun newestPending() = vehicles.filter { it.pending }
        .maxWithOrNull(compareBy<FleetVehicle> { it.lastDisconnectAt }.thenBy { it.id })

    fun bluetooth(address: String, connected: Boolean, now: Long): VehicleFleet {
        val target = vehicles.find { it.bluetoothAddress.isNotBlank() && it.bluetoothAddress.equals(address, true) }
            ?: return this
        // ACL/profile and foreground/manifest receivers can repeat the same transition.
        if (connected && target.connected) return this
        if (!connected && !target.connected && target.lastEvent == "DISCONNECTED") return this
        val at = maxOf(now, (vehicles.maxOfOrNull { it.lastEventAt } ?: 0) + 1)
        val changed = target.copy(connected = connected, locked = if (connected) false else target.locked,
            lastEvent = if (connected) "CONNECTED" else "DISCONNECTED", lastEventAt = at,
            lastDisconnectAt = if (connected) target.lastDisconnectAt else at)
        return copy(vehicles = vehicles.map { if (it.id == target.id) changed else it }, currentVehicleId = target.id)
    }

    fun adapterState(enabled: Boolean) = if (enabled) this else
        copy(vehicles = vehicles.map { if (it.connected) it.copy(connected = false) else it })

    fun confirm(id: String, disconnectAt: Long): VehicleFleet {
        val target = vehicle(id) ?: return this
        if (!target.pending || target.lastDisconnectAt != disconnectAt) return this
        return copy(vehicles = vehicles.map { if (it.id == id) it.copy(locked = true) else it })
    }

    fun confirmNfc(tagId: String): VehicleFleet {
        if (tagId !in configuredNfcTags) return this
        val target = newestPending() ?: return this
        return confirm(target.id, target.lastDisconnectAt)
    }

    fun saveVehicle(id: String, name: String, address: String, bluetoothName: String?): VehicleFleet {
        require(id.isNotBlank() && name.isNotBlank() && address.isNotBlank()) { "Enter a name and choose a Bluetooth device." }
        require(vehicles.none { it.id != id && it.bluetoothAddress.equals(address, true) }) {
            "That Bluetooth device already belongs to another vehicle."
        }
        val old = vehicle(id)
        val changedAddress = old != null && !old.bluetoothAddress.equals(address, true)
        val item = if (old != null && !changedAddress) old.copy(name = name, bluetoothAddress = address, bluetoothName = bluetoothName)
            else FleetVehicle(id, name, address, bluetoothName)
        return copy(vehicles = if (old == null) vehicles + item else vehicles.map { if (it.id == id) item else it },
            currentVehicleId = if (changedAddress && currentVehicleId == id) null else currentVehicleId)
    }

    fun deleteVehicle(id: String) = copy(vehicles = vehicles.filter { it.id != id },
        currentVehicleId = if (currentVehicleId == id) vehicles.filter { it.id != id && it.lastEventAt > 0 }
            .maxByOrNull { it.lastEventAt }?.id else currentVehicleId)

    fun addTag(id: String): VehicleFleet {
        require(id.isNotBlank()) { "NFC tag has no stable UID." }
        return copy(configuredNfcTags = (configuredNfcTags + id).distinct())
    }

    fun removeTag(id: String) = copy(configuredNfcTags = configuredNfcTags.filter { it != id })
}
