# Locksora Android Bluetooth and NFC

All configured vehicle MAC addresses are registered natively. Settings adds,
edits, and removes vehicles; no active vehicle is selected manually. Real vehicle
Bluetooth events choose the Home context automatically. Only a real matching
connection unlocks that vehicle. Disconnect reminders are immediate; late duplicate
disconnects never undo NFC confirmation.

In **Lock confirmation NFC tags**, add one or more global Locksora tags. Any tag
confirms the most recently disconnected pending vehicle. Tags survive vehicle
editing/deletion. If no vehicle is pending, a scan changes no lock state.
Notification/manual actions target the original vehicle and disconnect generation.

State is one JSON document in the existing `carlock_state` SharedPreferences,
under `fleet_state_v2`. Native code owns state, matching, notifications, and NFC;
React Native consumes revisioned snapshots. Open the upgraded app once to import
older JS-only vehicle profiles.

Foreground reader mode and native intent dispatch are retained. Passive NFC
scanning generally requires the phone to be unlocked. For background dispatch,
a blank tag avoids higher-priority URL/MIME routing to another app; Android may
show a chooser. Notification permission and platform restrictions still apply.

Build a new Android development/release APK. Expo Go or a JavaScript-only update
cannot supply this native API. Storage, migration, targeting, tests, and release
checks: [fleet implementation notes](../../docs/locksora-vehicles.md).
