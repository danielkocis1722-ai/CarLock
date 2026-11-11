package expo.modules.carlockbluetooth

private val a = FleetVehicle("a", "Citroën C4", "AA:01")
private val b = FleetVehicle("b", "Octavia", "BB:02")
private fun fleet() = VehicleFleet(listOf(a, b), listOf("TAG_A", "TAG_B"))
private fun pendingBoth() = fleet().bluetooth("AA:01", true, 1).bluetooth("AA:01", false, 2)
    .bluetooth("BB:02", true, 3).bluetooth("BB:02", false, 4)
private var passed = 0
private fun test(name: String, block: () -> Unit) { block(); passed++; println("PASS $name") }
private fun equal(expected: Any?, actual: Any?) { check(expected == actual) { "Expected $expected, got $actual" } }

fun main() {
    test("A connection changes only A and chooses A automatically") {
        val state = fleet().bluetooth("aa:01", true, 10)
        equal(b, state.vehicle("b")); equal(false, state.vehicle("a")!!.locked)
        equal(true, state.vehicle("a")!!.connected); equal("a", state.currentVehicleId)
    }
    test("B connection changes only B and chooses B automatically") {
        val state = fleet().bluetooth("BB:02", true, 10)
        equal(a, state.vehicle("a")); equal("IN_CAR", state.vehicle("b")!!.state)
        equal("b", state.currentVehicleId)
    }
    test("unregistered Bluetooth devices and missing addresses are ignored") {
        equal(fleet(), fleet().bluetooth("HEADPHONES", false, 10))
        equal(fleet(), fleet().bluetooth("", true, 10))
    }
    test("disconnect produces named targeted reminder and preserves other vehicle") {
        val state = fleet().bluetooth("BB:02", true, 1).bluetooth("BB:02", false, 2)
        val reminder = state.vehicle("b")!!.reminder()!!
        equal("b", reminder.vehicleId); equal(2L, reminder.disconnectAt)
        equal("Did you lock Octavia?", reminder.title); equal("vehicle:b", reminder.notificationTag)
        equal(a, state.vehicle("a")); equal("UNCONFIRMED", state.vehicle("b")!!.state)
    }
    test("notifications have distinct identities for each vehicle and each trip") {
        val state = pendingBoth()
        check(state.vehicle("a")!!.reminder()!!.notificationTag != state.vehicle("b")!!.reminder()!!.notificationTag)
        val old = state.vehicle("a")!!.reminder()!!
        val newer = state.bluetooth("AA:01", true, 5).bluetooth("AA:01", false, 6).vehicle("a")!!.reminder()!!
        check(old.disconnectAt != newer.disconnectAt)
    }
    test("notification action confirms its origin although another vehicle is current") {
        val state = pendingBoth(); val origin = state.vehicle("a")!!.reminder()!!
        val changed = state.confirm(origin.vehicleId, origin.disconnectAt)
        equal(true, changed.vehicle("a")!!.locked); equal(state.vehicle("b"), changed.vehicle("b"))
        equal("b", changed.currentVehicleId)
    }
    test("stale notification from a previous trip cannot confirm a new disconnect") {
        val state = pendingBoth(); val old = state.vehicle("a")!!.reminder()!!
        val newer = state.bluetooth("AA:01", true, 5).bluetooth("AA:01", false, 6)
        equal(newer, newer.confirm(old.vehicleId, old.disconnectAt))
    }
    test("global NFC confirms most recent pending disconnect, not an in-car display vehicle") {
        val state = fleet().bluetooth("AA:01", true, 1).bluetooth("AA:01", false, 2).bluetooth("BB:02", true, 3)
        val changed = state.confirmNfc("TAG_A")
        equal(true, changed.vehicle("a")!!.locked); equal(state.vehicle("b"), changed.vehicle("b"))
        equal("b", changed.currentVehicleId)
    }
    test("both global tags confirm the same latest pending vehicle") {
        val state = pendingBoth()
        equal(state.confirmNfc("TAG_A"), state.confirmNfc("TAG_B"))
        equal(true, state.confirmNfc("TAG_A").vehicle("b")!!.locked)
        equal(false, state.confirmNfc("TAG_A").vehicle("a")!!.locked)
    }
    test("successive NFC taps drain pending vehicles in disconnect order") {
        val changed = pendingBoth().confirmNfc("TAG_A").confirmNfc("TAG_A")
        check(changed.vehicles.all { it.locked })
        equal(null, changed.newestPending())
    }
    test("tag configured with only A still confirms B added later") {
        val state = VehicleFleet(listOf(a)).addTag("TAG_A").saveVehicle("b", "Octavia", "BB:02", null)
            .bluetooth("BB:02", true, 1).bluetooth("BB:02", false, 2).confirmNfc("TAG_A")
        equal(true, state.vehicle("b")!!.locked)
    }
    test("NFC with no pending vehicle or unknown tag changes nothing") {
        equal(fleet(), fleet().confirmNfc("TAG_A"))
        val connected = fleet().bluetooth("AA:01", true, 1)
        equal(connected, connected.confirmNfc("TAG_A"))
        equal(pendingBoth(), pendingBoth().confirmNfc("UNKNOWN"))
    }
    test("duplicate disconnect after NFC never undoes lock or recreates reminder") {
        val locked = pendingBoth().confirmNfc("TAG_A")
        equal(locked, locked.bluetooth("BB:02", false, 500))
        equal(null, locked.vehicle("b")!!.reminder())
    }
    test("late duplicate disconnect cannot steal current context or priority") {
        val state = pendingBoth()
        equal(state, state.bluetooth("AA:01", false, 500))
        equal("b", state.newestPending()!!.id)
    }
    test("adapter OFF preserves every lock and does not invent a real disconnect") {
        val before = fleet().bluetooth("AA:01", true, 1)
        val off = before.adapterState(false)
        equal(before.vehicles.map { it.locked }, off.vehicles.map { it.locked })
        equal(null, off.newestPending()); equal("a", off.currentVehicleId)
        equal(before.vehicle("a")!!.lastEventAt, off.vehicle("a")!!.lastEventAt)
        equal(off, off.confirmNfc("TAG_A"))
        equal(off, off.adapterState(false))
        equal(off, off.adapterState(true))
        equal(fleet(), fleet().adapterState(false).adapterState(true))
    }
    test("a real connection after NFC can unlock only that vehicle") {
        val state = pendingBoth().confirmNfc("TAG_A")
        val changed = state.bluetooth("BB:02", true, 100)
        equal(false, changed.vehicle("b")!!.locked); equal(true, changed.vehicle("b")!!.connected)
        equal(state.vehicle("a"), changed.vehicle("a"))
    }
    test("equal clock timestamps and clock rollback still order real disconnects") {
        val state = fleet().bluetooth("AA:01", true, 100).bluetooth("AA:01", false, 100)
            .bluetooth("BB:02", true, 1).bluetooth("BB:02", false, 1)
        equal("b", state.newestPending()!!.id)
    }
    test("add rename edit and delete do not modify global tags") {
        val start = fleet()
        val changed = start.saveVehicle("c", "New car", "CC:03", null)
            .saveVehicle("a", "Renamed", "DD:04", null).deleteVehicle("b").deleteVehicle("a").deleteVehicle("c")
        equal(start.configuredNfcTags, changed.configuredNfcTags)
        equal(emptyList<FleetVehicle>(), changed.vehicles); equal(null, changed.currentVehicleId)
    }
    test("same-address rename preserves confirmed lock and real event context") {
        val state = pendingBoth().confirmNfc("TAG_A")
        val changed = state.saveVehicle("b", "My Octavia", "bb:02", "SKODA")
        equal(true, changed.vehicle("b")!!.locked)
        equal(state.vehicle("b")!!.lastDisconnectAt, changed.vehicle("b")!!.lastDisconnectAt)
        equal(state.currentVehicleId, changed.currentVehicleId)
    }
    test("tag enrollment/removal never changes any vehicle state") {
        val state = pendingBoth()
        val changed = state.addTag("TAG_C").addTag("TAG_C").removeTag("TAG_A")
        equal(state.vehicles, changed.vehicles); equal(state.currentVehicleId, changed.currentVehicleId)
        equal(listOf("TAG_B", "TAG_C"), changed.configuredNfcTags)
    }
    test("duplicate Bluetooth address is rejected") {
        check(runCatching { fleet().saveVehicle("c", "Duplicate", "aa:01", null) }.isFailure)
    }
    test("legacy migration preserves selected vehicle, state, pending timestamp and NFC UID") {
        val state = FleetCodec.legacy("CITROEN", "AA:01", true, false, "DISCONNECTED", 123, "OLD_TAG")
        equal(true, state.vehicles.single().locked); equal(false, state.vehicles.single().connected)
        equal(123L, state.vehicles.single().lastDisconnectAt); equal(listOf("OLD_TAG"), state.configuredNfcTags)
        equal(null, state.newestPending())
    }
    test("migration unions every profile tag and native tag without resetting native lock") {
        val native = FleetCodec.legacy("CITROEN", "AA:01", true, false, "DISCONNECTED", 123, "NATIVE_TAG")
        val raw = """{"activeVehicleId":"b","vehicles":[
            {"id":"a","name":"Citroën C4","bluetoothAddress":"aa:01","nfcTagId":"TAG_A"},
            {"id":"b","name":"Octavia","bluetoothAddress":"BB:02","nfcTagId":"TAG_B"}]}"""
        val changed = FleetCodec.importProfiles(native, raw)
        equal(2, changed.vehicles.size); equal(true, changed.vehicles[0].locked)
        equal(native.currentVehicleId, changed.currentVehicleId) // manual active ID ignored
        equal(setOf("NATIVE_TAG", "TAG_A", "TAG_B"), changed.configuredNfcTags.toSet())
        equal(changed, FleetCodec.importProfiles(changed, raw))
    }
    test("migration preserves an unconfirmed disconnect for background NFC") {
        val state = FleetCodec.legacy("CITROEN", "AA:01", false, false, "DISCONNECTED", 123, "TAG")
        equal(true, state.confirmNfc("TAG").vehicles.single().locked)
    }
    test("versioned JSON roundtrip preserves all states, tags, context and revision") {
        val state = pendingBoth().confirmNfc("TAG_A").copy(legacyProfilesImported = true, revision = 25)
        equal(state, FleetCodec.decode(FleetCodec.encode(state)))
        check(runCatching { FleetCodec.decode("{\"schemaVersion\":99}") }.isFailure)
    }
    test("deleting all migrated vehicles cannot resurrect old profiles on later import") {
        val state = FleetCodec.importProfiles(fleet(), null).deleteVehicle("a").deleteVehicle("b")
        equal(state, FleetCodec.importProfiles(state, """{"vehicles":[{"id":"old","name":"Old","bluetoothAddress":"AA:01"}]}"""))
    }
    println("$passed native fleet tests passed")
}
