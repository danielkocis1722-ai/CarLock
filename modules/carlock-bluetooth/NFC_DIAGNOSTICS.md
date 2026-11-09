# Locked-phone NFC investigation

## Findings from the current implementation

Both NFC paths are present:

| Path | Implementation | Delivery |
| --- | --- | --- |
| Foreground confirmation | `CarlockBluetoothModule.enableNfcReader()`; enabled on activity resume/initial listener registration, disabled on pause | `NfcAdapter.ReaderCallback`; not an NFC intent |
| Enrollment | `CarlockNfcSetupActivity`, private activity using reader mode while resumed | Reader callback; saves the tag UID only |
| Background/cold confirmation | Exported `CarlockNfcActivity`, `Theme.NoDisplay`, finishes inside `onCreate` | Manifest filters for `ACTION_TECH_DISCOVERED` and fallback `ACTION_TAG_DISCOVERED` |

The confirmation handler accepts `ACTION_NDEF_DISCOVERED` too, but **there is no
NDEF manifest filter**. A URL/MIME NDEF tag can be routed elsewhere before the
technology fallback. A foreground reader callback does not establish that
intent dispatch works. Enrollment does not establish background dispatch either.

No new manifest filters, lock-screen flags, permissions, polling settings, NFC
technology flags, or Bluetooth transitions are introduced by this diagnostic
change. Normal confirmation still occurs on a matching real scan.

## Platform conclusion versus device observations

Android documents normal tag discovery for an unlocked screen. Public reader
mode is activity/foreground scoped. The current AOSP NFC service normally gates
tag polling on `SCREEN_STATE_ON_UNLOCKED`; separate locked-screen cases include
provisioning and protected NFC unlock handlers. This supports **a platform
policy/security boundary**, rather than a missing ordinary manifest flag.
Screen-on/locked remains a separate state from screen-on/unlocked. [Android NFC
basics](https://developer.android.com/develop/connectivity/nfc/nfc),
[reader mode API](https://developer.android.com/reference/android/nfc/NfcAdapter#enableReaderMode(android.app.Activity,android.nfc.NfcAdapter.ReaderCallback,int,android.os.Bundle)),
[AOSP NFC service](https://android.googlesource.com/platform/packages/modules/Nfc/+/refs/heads/main/NfcNci/src/com/android/nfc/NfcService.java)

There is no supported public API/manifest switch that lets an ordinary app
force unattended reading of passive tags across the lock-screen boundary.
`showWhenLocked`/`setShowWhenLocked` controls activity visibility after a launch;
`turnScreenOn` controls waking a launched activity. Neither requests NFC discovery
or delivers a missing tag intent. This investigation deliberately does not set
either flag. Newer `setDiscoveryTechnology` is also foreground scoped.
[Activity API](https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)),
[discovery technology API](https://developer.android.com/reference/android/nfc/NfcAdapter#setDiscoveryTechnology(android.app.Activity,int,int))

Host/off-host card emulation, payment behavior, and `requireDeviceUnlock` or
`requireDeviceScreenOn` settings for card-emulation services concern a phone
responding to an external reader. They do not make a phone read a passive sticker.
Likewise, a disabled Secure NFC setting is not proof that tag polling is allowed
while locked. Secure NFC status is logged only as context.
[Card emulation](https://developer.android.com/develop/connectivity/nfc/hce),
[Secure NFC](https://source.android.com/docs/core/connect/secure-nfc)

**No physical device result has been collected here.** OEM firmware, NFC service
version, settings, and activity-launch policy can change observed behavior.
We cannot classify this particular phone as an OEM issue without its captures.

## What the instrumentation proves

All diagnostic entries use `Log.d("CarLockNFC", ...)`. Logs are enabled by default
for debuggable APKs, or explicitly using `log.tag.CarLockNFC=DEBUG` for an APK
that retains debug logging. No tag UID, NDEF payload, or intent URI is logged.

Each entry includes elapsed time, PID/thread, `interactive`, `keyguardLocked`,
`deviceLocked`, `keyguardSecure`, NFC availability/enabled/Secure NFC status,
configured-tag presence, persisted `connected`/`locked`, SDK, manufacturer/model,
and build. `isInteractive` distinguishes interactive operation from sleep/AOD;
use system display/power dumps to establish the precise screen state.
Keyguard visibility and device authentication status can differ, e.g. with
biometrics, trust agents, or swipe-only keyguard. Check both logged lock values.

| Stage | Meaning |
| --- | --- |
| `reader.enable.request`, `.returned`, `.skipped` | Reader configuration attempted, returned, or skipped for no configured tag. Return does not prove controller polling. |
| `module.activity.background`, `reader.disable.*` | React activity paused and reader mode disabled by the existing code. |
| `device.broadcast` | Screen on/off, user present/first-unlock, or NFC adapter-state event while the debug process is alive. Snapshot may precede final keyguard settling. |
| `reader.callback` | Tag delivered through foreground confirmation reader mode, with lock state sampled at callback time. |
| `setup.reader.callback` | Enrollment reader delivery; not lock confirmation. |
| `dispatch.activity.onCreate` / `.onNewIntent` | `CarlockNfcActivity` launch/delivery, exact action, intent flags, and presence of tag/NDEF extras. |
| `dispatch.tag.parsed` / `.missing` / `dispatch.action.ignored` | Whether the handler received a usable tag and an accepted action. |
| `confirmation.tag.match` | Whether the UID matches the configured tag. |
| `confirmation.preferences.applied`, `.complete` | Updated in-memory SharedPreferences, async disk write requested, reminder/alarm cancellation completed. |
| `bridge.onLockStateChanged` | Native event emission attempted while the React Native module exists; not proof the JS UI rendered. |

`CarlockNfcActivity` finishes in `onCreate`, so `onResume` need not occur. Its
absence alone does not mean launch failed. A cold NFC launch does not create the
React Native module or the diagnostic screen observer. There is no keepalive.

**App silence cannot distinguish controller polling disabled, controller saw no
tag, OS dispatch rejected, another app chosen, or an unstarted process.** Normal
third-party apps cannot observe all low-level tag detections. System logs can
provide positive evidence; vendor/user builds may omit it. A silent system
capture is also inconclusive. An NFC sound alone does not prove app delivery.

## Capture with adb (no root)

Rebuild/install this native implementation. Open once, select the vehicle, enroll
the tag, then remove it from the antenna. Keep Bluetooth testing separate.
Authorize USB debugging while unlocked before starting.

Enable logs before testing, including before any cold-process test:

```sh
adb shell setprop log.tag.CarLockNFC DEBUG
adb logcat -v threadtime "CarLockNFC:D" "CarLockNFCTest:I" "*:S"
```

In another terminal, retain **all processes**, since filtering by app PID loses
the NFC service, activity launch denials, and cold-start events:

```sh
adb logcat -b all -v threadtime > nfc-system-log.txt
```

Before/after each case, take fresh snapshots (give files case-specific names):

```sh
adb shell dumpsys nfc > nfc-state.txt
adb shell dumpsys power > power-state.txt
adb shell dumpsys window policy > keyguard-state.txt
adb shell dumpsys package com.danom.CarLock > package-state.txt
```

Look in the full log for NFC discovery/dispatch, screen-state/polling changes,
and ActivityTaskManager launch records/denials. NFC service tag names and dump
fields vary by Android/OEM version; a nonzero poll mask alone is not proof an
individual tag was detected. Dumps can contain identifiers; review before sharing.

Mark cases without touching app state:

```sh
adb shell log -t CarLockNFCTest "CASE screen_on_locked tap_begin"
```

For every case: hold tag near the antenna briefly, remove it completely, mark
the end, then collect dumps. Remove it **before unlocking**, otherwise a scan
on unlock can be mistaken for a locked-screen scan. Repeat at least three times.
Wake without authenticating, and verify lock state; biometric wake may unlock.

| Case | App condition | What to record |
| --- | --- | --- |
| Screen on, unlocked | Home visible | Positive control: `reader.callback`, match, completion. |
| Screen on, unlocked | App backgrounded | Intent positive control: `dispatch.activity.onCreate`, action and tag extra. Prefer configured blank tag; note chooser/other app. |
| Screen off, locked | Background, process alive | Reader disable/screen events, system polling/detection, any NFC activity entry with lock state. AOD is separate from an interactive display. |
| Screen on, still locked | Same process state | Repeat after waking without authenticating; check both keyguard/device lock values. |
| Screen on, unlocked | Same background condition | Positive control immediately after each locked test, with a fresh tap. |
| Both locked cases | Background process reclaimed | Repeat to test intent cold launch independently of the JS runtime. Do not force-stop as a substitute for ordinary process reclamation. |

Use the configured tag first. If background delivery also fails unlocked, compare
with a blank tag to isolate NDEF routing; do not reformat a tag just to collect
logs. An unrelated UID should log a mismatch and leave confirmation unchanged.
Record Secure NFC/OEM setting values without changing them during a case.

Optional **synthetic activity-only** control, while locked:

```sh
adb shell am start -W -n com.danom.CarLock/expo.modules.carlockbluetooth.CarlockNfcActivity -a android.nfc.action.TECH_DISCOVERED
```

This supplies no `Tag` extra and cannot confirm the car. Label it synthetic.
It tests an adb-shell activity start, which has different privileges and routing
from NFC. It proves neither RF detection nor NFC delivery while locked.

Restore release logging to its default afterward:

```sh
adb shell setprop log.tag.CarLockNFC INFO
```

## Assigning the limitation from evidence

| Evidence | Classification |
| --- | --- |
| App intent/callback entry while locked; tag missing/mismatch or later processing failure | Our receiving/confirmation code or input/configuration; inspect the next stage. |
| System detects tag but routes to another app/chooser, or reports no matching activity | Android dispatch and current filters/defaults; NDEF filter absence is a known code configuration limitation. |
| System records NFC activity start rejection/deferment while locked | Android activity/keyguard dispatch policy; compare unlocked control. |
| NFC dump/log explicitly shows polling suppressed by lock/screen state and unlocked control works | Platform lock-state policy; OEM implementation may additionally constrain it. |
| Different phones/settings give different results under controlled conditions | Device/OEM behavior; retain build/NFC service/settings evidence. |
| No app entries and no useful system detection/policy entries | Inconclusive below the app boundary. Do not claim hardware detection failed or identify an OEM fault. |

Validation here: TypeScript and diff checks; code inspection confirms no manifest
or Bluetooth transition edits. APK compilation and the physical-device matrix
remain unverified: this workstation has no JDK, Android SDK, or adb device access.
