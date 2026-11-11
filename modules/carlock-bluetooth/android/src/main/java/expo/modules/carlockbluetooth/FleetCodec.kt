package expo.modules.carlockbluetooth

import org.json.JSONArray
import org.json.JSONObject

/** Versioned persistence and one-time migration, independent of Android for JVM tests. */
internal object FleetCodec {
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)

    fun encode(fleet: VehicleFleet): String = JSONObject().apply {
        put("schemaVersion", 2)
        put("revision", fleet.revision)
        put("legacyProfilesImported", fleet.legacyProfilesImported)
        put("currentVehicleId", fleet.currentVehicleId ?: JSONObject.NULL)
        put("configuredNfcTags", JSONArray(fleet.configuredNfcTags))
        put("vehicles", JSONArray().apply {
            fleet.vehicles.forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("name", item.name)
                put("bluetoothAddress", item.bluetoothAddress)
                put("bluetoothName", item.bluetoothName ?: JSONObject.NULL)
                put("locked", item.locked); put("connected", item.connected)
                put("lastEvent", item.lastEvent); put("lastEventAt", item.lastEventAt)
                put("lastDisconnectAt", item.lastDisconnectAt)
                put("pending", item.pending); put("state", item.state)
            }) }
        })
    }.toString()

    fun decode(raw: String): VehicleFleet {
        val json = JSONObject(raw)
        require(json.getInt("schemaVersion") == 2) { "Unsupported Locksora storage version." }
        val items = json.getJSONArray("vehicles")
        val tags = json.getJSONArray("configuredNfcTags")
        return VehicleFleet(
            vehicles = (0 until items.length()).map { index -> items.getJSONObject(index).let {
                FleetVehicle(it.getString("id"), it.getString("name"), it.getString("bluetoothAddress"),
                    it.nullableString("bluetoothName"), it.getBoolean("locked"), it.getBoolean("connected"),
                    it.getString("lastEvent"), it.getLong("lastEventAt"), it.getLong("lastDisconnectAt"))
            } },
            configuredNfcTags = (0 until tags.length()).map { tags.getString(it) }.distinct(),
            currentVehicleId = json.nullableString("currentVehicleId"),
            legacyProfilesImported = json.getBoolean("legacyProfilesImported"),
            revision = json.getLong("revision")
        )
    }

    fun legacy(name: String?, address: String?, locked: Boolean, connected: Boolean,
               event: String, at: Long, tagId: String?): VehicleFleet {
        val item = if (!address.isNullOrBlank() || !name.isNullOrBlank()) FleetVehicle(
            id = "legacy-${address?.uppercase() ?: "unconfigured"}",
            name = name ?: "My vehicle", bluetoothAddress = address ?: "", bluetoothName = name,
            locked = locked, connected = connected, lastEvent = event, lastEventAt = at,
            lastDisconnectAt = if (event == "DISCONNECTED") at else 0
        ) else null
        return VehicleFleet(vehicles = listOfNotNull(item),
            configuredNfcTags = listOfNotNull(tagId?.takeIf { it.isNotBlank() }),
            currentVehicleId = item?.takeIf { it.lastEvent in listOf("CONNECTED", "DISCONNECTED") }?.id)
    }

    fun importProfiles(fleet: VehicleFleet, raw: String?): VehicleFleet {
        if (fleet.legacyProfilesImported) return fleet
        val vehicles = fleet.vehicles.toMutableList()
        val tags = fleet.configuredNfcTags.toMutableList()
        if (raw != null) {
            val profiles = JSONObject(raw).getJSONArray("vehicles")
            for (index in 0 until profiles.length()) {
                val profile = profiles.getJSONObject(index)
                val name = profile.getString("name")
                val address = profile.optString("bluetoothAddress", "").trim()
                val tagId = profile.nullableString("nfcTagId")
                if (!tagId.isNullOrBlank()) tags.add(tagId)
                val existing = vehicles.indexOfFirst {
                    if (address.isBlank()) it.bluetoothAddress.isBlank() && it.name == name
                    else it.bluetoothAddress.equals(address, true)
                }
                if (existing >= 0) {
                    // Native connection/lock state wins over legacy JS profile metadata.
                    vehicles[existing] = vehicles[existing].copy(name = name,
                        bluetoothName = profile.nullableString("bluetoothName") ?: vehicles[existing].bluetoothName)
                } else {
                    val preferredId = profile.getString("id")
                    val id = if (vehicles.any { it.id == preferredId }) "import-$index-$preferredId" else preferredId
                    vehicles.add(FleetVehicle(id, name, address, profile.nullableString("bluetoothName")))
                }
            }
        }
        return fleet.copy(vehicles = vehicles, configuredNfcTags = tags.distinct(), legacyProfilesImported = true)
    }
}
