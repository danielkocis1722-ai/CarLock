# Locksora vehicle profiles and guide

This work starts from `codex/nfc-lock-confirmation` (stable NFC), without the
separate lock-screen diagnostic changes.

## Storage and native mapping

`src/data/vehicle-store.ts` stores one JSON document in existing AsyncStorage:
`@locksora/vehicles:v1`, containing `vehicles` and `activeVehicleId`. Each vehicle
has a stable ID, friendly name, Bluetooth name/address, optional NFC UID, and
primary flag. Native `selectedCarName`, `selectedCarAddress`, and `nfcTagId` are
imported as the first vehicle on upgrade. Migration and ordinary refreshes do
not call the native selection setter or reset a confirmed lock.

Only one profile is monitored. `activateVehicle(name, address, nfcTagId)` writes
the active profile's friendly name, Bluetooth MAC, and tag to the existing
`carlock_state` SharedPreferences in one edit. MAC matching still takes priority
in the existing receivers. A profile without a tag explicitly clears the native
tag; switching back restores that profile's saved tag. Inactive profiles never
participate in native scanning or notifications.

When the Bluetooth address changes, the native setter uses the original
selected-car reset policy: connected=false, locked=true, lastEvent=NONE,
lastEventAt=0, and reminder cancellation. Same-address name/tag edits preserve
the current connected/locked values. Refresh never probes an arbitrary Bluetooth
profile to infer an unlock. Existing real connect/disconnect handling is intact.

Mutations are serialized. The desired profile is persisted with a temporary
`nativeSyncPending` marker before native activation; successful activation clears
it. Refresh repairs interrupted handoffs before importing native tag changes.
Activation errors roll the stored selection back. Native NFC enrollment remains
the tag source of truth for the active profile and is reconciled when JS resumes.

Adding a vehicle selects it automatically only when it is the first vehicle.
Friendly names can be edited without losing the tag. Changing its Bluetooth
device clears the tag to prevent accidental reuse. Duplicate Bluetooth addresses
are rejected. The final vehicle cannot be deleted; an initially empty install
shows an Add vehicle prompt. Deleting the active vehicle selects a configured
replacement and restores its tag.

## NFC enrollment and UI

Settings names the target vehicle before opening enrollment. Configuring an
inactive profile explicitly makes it active first. The existing private native
enrollment activity displays that vehicle's name and instructions. It reports an
activity result through `onNfcEnrollmentFinished`, including successful scans of
the same tag, so the UI can distinguish success from cancellation. Reconciliation
also captures completed enrollment after process interruption. Enrollment still
only saves the UID and never confirms a lock. Removing a tag never changes lock
state.

Enrollment uses Android's public `NfcAdapter.ignore` API before returning to the
main activity, so a tag held against the phone is suppressed until it is removed
and tapped again. The debounce is 500 ms; physical-device validation is still
needed for the controller's removal detection. This guard is confined to setup;
normal NFC confirmation and background dispatch are unchanged.

The application label and user-facing copy use Locksora. Package/application ID
`com.danom.CarLock`, URL scheme, Expo slug/project ID, Kotlin classes/packages,
native module name, preferences, notification 1001, legacy alarm identity, and
repository name are unchanged.

`src/components/locksora-guide.tsx` supplies six horizontally paged guide pages,
pagination, Skip, Next, and Done. Home opens it automatically until Skip/Done is
successfully persisted as `@locksora/guide-seen:v1=true`. The ? action reopens it.
The guide states that passive NFC confirmation generally needs an unlocked
Android phone and that the user must physically lock the vehicle first.

## Validation

- `npm run test:vehicles`: model migration, tag isolation, add/edit/delete,
  duplicate devices, native-failure rollback, serialized switches, and recovery
  after interrupted switches/tag removal. Uses Node's test runner and TypeScript
  stripping (Node 22.13+ as required by the Expo project).
- `npx tsc --noEmit` checks the React/native interface and UI code.
- `npx expo export --platform android` checks the Android JavaScript bundle.
- A new APK is required for the Android app label, active-profile API, and
  enrollment-result bridge. This is not an Expo Go-only or JS-only update.

Native Android compilation and physical-device checks require the Android
SDK/JDK and a phone, which are unavailable on this workstation.

Before release, check on a phone:

1. Upgrade with a selected vehicle, saved NFC tag, and confirmed lock. Verify the
   profile/tag migrates and no guide dismissal or profile refresh changes locking.
2. Add another vehicle; the first stays active. Rename it, switch both ways, and
   verify the appropriate Bluetooth MAC and NFC tag are used each time.
3. Reconfigure using both the same tag and a different tag. Verify success feedback;
   Back/cancel must leave the old tag intact. Enrollment must not set locked=true.
4. Remove active/inactive tags and change a Bluetooth device. Verify no previous
   vehicle's tag can confirm the new active vehicle.
5. Verify selected connect → IN_CAR, disconnect → UNCONFIRMED + immediate reminder,
   matching NFC → LOCKED + removal of notification 1001 and the legacy alarm.
   Adapter OFF/ON and late duplicate disconnects must not overwrite confirmed locking.
6. Exercise deletion of inactive/active profiles and the last-profile safeguard.
   Kill/relaunch around a switch and enrollment, then verify reconciliation.
7. On a fresh install verify onboarding; swipe all pages, Skip/Done, restart, and
   reopen with ?. Check small screens, font scaling, scroll, and permission denial.
