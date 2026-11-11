# Locksora fleet and global NFC

This branch extends `codex/locksora-vehicles-onboarding` without including the
separate NFC lock-screen diagnostics branch. There is no manual active/primary
vehicle selection or vehicle-specific NFC ownership.

## Exact native storage

One JSON string under `fleet_state_v2` in existing `carlock_state`
SharedPreferences is authoritative:

```json
{
  "schemaVersion": 2,
  "revision": 17,
  "legacyProfilesImported": true,
  "currentVehicleId": "vehicle-b",
  "configuredNfcTags": ["TAG_A", "TAG_B"],
  "vehicles": [{
    "id": "vehicle-b",
    "name": "Octavia",
    "bluetoothAddress": "AA:BB:CC:DD:EE:FF",
    "bluetoothName": "SKODA",
    "locked": false,
    "connected": false,
    "lastEvent": "DISCONNECTED",
    "lastEventAt": 123456789,
    "lastDisconnectAt": 123456789,
    "pending": true,
    "state": "UNCONFIRMED"
  }]
}
```

`pending` and `state` are derived when serializing and never trusted on decode.
Pending means disconnected, unlocked, and backed by a real disconnect and a
positive disconnect timestamp. Each mutation writes the entire JSON in one edit.
`CarlockVehicleStore` serializes mutations and notification side effects with a
process-wide monitor. Revision increases only on changes. Invalid JSON/schema
raises an error instead of silently replacing saved data.

JS issues metadata/confirmation commands and consumes `onFleetChanged` snapshots.
It never writes live connection/lock state or copies a profile into legacy keys.
A revision check prevents delayed RPC snapshots overwriting newer native events.
There is no database or new production dependency.

## Bluetooth and automatic UI context

Manifest and foreground deliveries use the same `CarlockBluetoothReceiver` and
`VehicleFleet.bluetooth` rules. Every registered nonempty MAC is eligible;
matching is case-insensitive. Unknown devices are ignored. Name matching and
the implicit CITROEN fallback are removed.

- Connection changes only that vehicle to connected=true, locked=false.
- Disconnect changes only its connected flag and records its real disconnect.
  If locking is unconfirmed, its reminder appears immediately. No alarm is scheduled.
- Adapter OFF clears connection flags without changing locks, event history,
  disconnect timestamps, or UI context. Adapter ON changes no vehicle state.

ACL/profile and foreground/manifest duplicates are idempotent. A late duplicate
disconnect cannot undo NFC locking, refresh pending priority, repost a confirmed
reminder, or steal UI context. Real event timestamps are monotonically increasing
across the fleet, including same-millisecond events and wall-clock rollback.

`currentVehicleId` follows the latest nonduplicate real configured-vehicle
Bluetooth event. Settings and enrollment cannot select it. Confirmation changes
the targeted state without switching context to an arbitrary vehicle: if Home
shows an in-use vehicle, NFC can still confirm another genuinely pending vehicle.
With no real event, Home waits for a connection instead of selecting the first
vehicle. Settings shows all stored vehicle states.

New/unobserved vehicles retain the previous neutral defaults: connected=false,
locked=true. Adding/editing cannot invent an unlock. Same-address renames preserve
state; address changes reset only that vehicle and remove its old reminder.
Deleting a current vehicle falls back to remaining real history; deleting the
last vehicle leaves a safe empty registry and retains global tags.

## Notifications and actions

Each vehicle uses `(tag="vehicle:<id>", id=1001)` on the existing
`carlock_reminders` channel. The title is `Did you lock <friendly name>?`.
Different vehicles can have reminders visible simultaneously.

Action extras carry `vehicleId` and `disconnectAt`; PendingIntent data URIs also
include the vehicle ID, disconnect generation, and action. Extras alone do not
differentiate PendingIntents. The receiver checks the originating vehicle is
still pending at that generation. Stale actions cannot confirm or cancel a new
trip; missing legacy extras never fall back to the current vehicle.

`I locked it` confirms that origin; `Keep unlocked` only dismisses its reminder.
Home manual confirmation uses the same ID/generation validation. Reconnection,
confirmation, address change, and deletion remove only that vehicle's reminder.
Cleanup also cancels old untagged notification 1001 and the exact legacy alarm
PendingIntent (request code 2001, `CarlockReminderReceiver`). An old alarm delivery
does not create an untargeted fleet reminder.

## Global NFC

Enrollment appends a deduplicated UID to `configuredNfcTags` without changing any
vehicle or UI context. Tags survive vehicle add/edit/delete and address changes.
Individual tags can be removed in Settings.

A matching scan selects the pending vehicle with greatest `lastDisconnectAt`,
sets only its locked=true, and removes its reminder plus legacy cleanup. Unknown
tags and no-pending scans are no-ops. Another deliberate scan confirms the next
pending vehicle. Confirmation does not blindly target the displayed vehicle.

Foreground reader mode and `CarlockNfcActivity` intent dispatch still work through
native persistence without a React runtime. Enrollment retains the public
`NfcAdapter.ignore` fresh-tap guard before returning. Branding, guide pagination,
first-launch persistence, and unlocked-phone NFC messaging are preserved. No
lock-screen bypass, privileged API, or keepalive service is introduced.

## Migration

On first native access, legacy selectedCarName/selectedCarAddress, locked,
connected, lastEvent/lastEventAt, and nfcTagId seed the fleet. Lock/connection state
and real event context are preserved exactly; the old UID becomes global. A
name-only record is retained but cannot match Bluetooth until given an address.

On first upgraded JS launch, the existing `@locksora/vehicles:v1` AsyncStorage
document is passed to native migration. All profiles merge by MAC. Native IDs
and runtime state win on overlap; friendly metadata is imported. Every saved
per-profile UID is unioned into global tags. Manual activeVehicleId and primary
flags are ignored. Previously unobserved profiles use neutral defaults.

The atomic `legacyProfilesImported` flag makes import idempotent, so deletion or
tag removal cannot be undone by reimport. Old preferences and the JS document
remain backups only. The onboarding seen flag is unchanged.

Open the upgraded app once to import profiles previously stored only in JS.
Before that launch, native background code can migrate only the old native
selected-car fields; it cannot access an unopened JS AsyncStorage document.
After import, every vehicle and tag is available with React closed.

## Validation and release

- `npm run test:vehicles` tests JS migration forwarding, native command boundaries,
  targeted confirmation, queue recovery, and snapshot revision ordering.
- `scripts/test-native-fleet.ps1` compiles/runs the actual production Kotlin
  engine and codec on a JVM. See [native test instructions](../tests/native/README.md).
- TypeScript, Android JS export, XML and whitespace checks are run separately.
- Android adapters are compiled against Android API 36 and AndroidX Core. That
  standalone compile excludes the Expo module bridge; a full Gradle/APK build
  and physical-device testing remain required.

A **new APK is required** for the native interface and storage changes. No push
or merge has been performed.

On a phone, verify A/B connections, unrelated headphones, simultaneous reminders,
cross-target/stale actions, global tags, no-pending scans, foreground/background
NFC, enrollment, power toggles, restarts, both old migration paths, and deleting
all vehicles. Android must deliver the underlying event; force-stop, lock-screen,
OEM, and notification-permission restrictions remain platform constraints.
