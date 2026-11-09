# Android lock confirmation

Build a new Android development/release APK after changing this module; Expo Go
does not load this native implementation.

Select a paired vehicle in Settings, enable NFC in Android settings, then tap
**Configure NFC tag** and scan a tag. Enrollment stores its UID as `nfcTagId` in
the existing `carlock_state` SharedPreferences. Enrollment does not confirm a lock.
Changing the selected vehicle clears the tag so a previous car's tag cannot
confirm the new car.

A selected-vehicle connection still saves `connected=true, locked=false`.
A selected-vehicle disconnect saves `connected=false` and immediately posts the
existing reminder if locking is unconfirmed. No new alarm is scheduled.
Disconnects and adapter power changes do not overwrite `locked`: in particular,
duplicate disconnect broadcasts arriving after NFC confirmation cannot undo it.

A matching NFC scan saves `locked=true`, cancels the old alarm PendingIntent
(request code 2001), and removes notification 1001. Manual and notification lock
confirmation use the same helper. SharedPreferences changes emit
`onLockStateChanged` through the existing Expo event bridge; the home screen
derives `LOCKED` from that value. Bluetooth connection events remain unchanged.

When the app is visible, native reader mode handles tags without leaving the UI.
When it is closed/backgrounded, Android can dispatch a tag to `CarlockNfcActivity`,
which confirms entirely in native code without starting React Native. Use a
blank tag for this path: an NDEF tag containing a URL or another app's payload
may be routed to that app before Android considers the technology filter.
Android may show an app chooser. NFC must be enabled and the phone generally
must be unlocked; force-stop and device-specific NFC restrictions still apply.
Notifications also require Android notification permission.

For lock-screen findings, diagnostic stages, and adb capture instructions, see
[NFC_DIAGNOSTICS.md](NFC_DIAGNOSTICS.md).

## Device verification

1. Select the car and enroll tag A. Verify enrollment leaves the lock state alone.
2. Connect the selected car: `IN_CAR`, connected, unlocked. Connect/disconnect an
   unrelated device and verify no car state change or reminder.
3. Disconnect the car with the UI open, then repeat with it backgrounded: verify
   `UNCONFIRMED` and an immediate reminder, with no 15-second timer.
4. Scan tag B: state and reminder must remain unchanged. Scan tag A: verify
   `LOCKED`, an immediate UI update, and removal of the visible reminder.
5. Repeat tag A confirmation with the UI closed/process reclaimed. Reopen and
   verify persisted `locked=true` and no reminder. Verify a late duplicate
   disconnect does not undo confirmation or repost the reminder.
6. Turn Bluetooth OFF then ON while locked: verify it stays locked. Only an
   actual selected-car connection should make it unlocked again.
7. Confirm using each existing manual/notification action and verify its behavior.
   Change cars and verify the old NFC tag no longer confirms the selected car.
