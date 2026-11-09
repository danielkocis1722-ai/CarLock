package expo.modules.carlockbluetooth

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/** Read-only diagnostics. No polling, keyguard, dispatch, or lock-state policy changes. */
internal object CarlockNfcDiagnostics {
    private const val LOG_TAG = "CarLockNFC"

    private fun enabled(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 ||
            Log.isLoggable(LOG_TAG, Log.DEBUG)

    fun log(context: Context, stage: String, details: String = "") {
        // Diagnostics must never prevent the existing NFC path from running.
        try {
            if (!enabled(context)) return
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val adapter = NfcAdapter.getDefaultAdapter(context)
            val prefs = context.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
            val secureNfc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && adapter != null) {
                "supported=${adapter.isSecureNfcSupported},enabled=${adapter.isSecureNfcEnabled}"
            } else "unavailable"
            Log.d(LOG_TAG, "stage=$stage elapsedMs=${SystemClock.elapsedRealtime()} " +
                "pid=${android.os.Process.myPid()} thread=${Thread.currentThread().name} " +
                "interactive=${power.isInteractive} keyguardLocked=${keyguard.isKeyguardLocked} " +
                "deviceLocked=${keyguard.isDeviceLocked} keyguardSecure=${keyguard.isKeyguardSecure} " +
                "nfcPresent=${adapter != null} nfcEnabled=${adapter?.isEnabled} secureNfc=[$secureNfc] " +
                "configured=${!prefs.getString("nfcTagId", null).isNullOrBlank()} " +
                "connected=${prefs.getBoolean("connected", false)} locked=${prefs.getBoolean("locked", true)} " +
                "sdk=${Build.VERSION.SDK_INT} manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} " +
                "build=${Build.DISPLAY} $details")
        } catch (e: Exception) {
            Log.d(LOG_TAG, "stage=$stage diagnosticSnapshotFailed=${e.javaClass.simpleName}")
        }
    }

    fun intent(context: Context, stage: String, intent: Intent) {
        // Do not log URI contents, arbitrary extras, raw tag UID, or NDEF payloads.
        log(context, stage, "action=${intent.action} flags=0x${intent.flags.toString(16)} " +
            "hasTagExtra=${intent.hasExtra(NfcAdapter.EXTRA_TAG)} " +
            "hasNdefExtra=${intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)}")
    }

    fun tag(context: Context, stage: String, tag: Tag) {
        log(context, stage, "uidBytes=${tag.id.size} techs=${tag.techList.joinToString(",")}")
    }

    fun observeScreen(context: Context): BroadcastReceiver? {
        try {
            return registerScreenObserver(context)
        } catch (e: Exception) {
            log(context, "device.observer.failed", "exception=${e.javaClass.simpleName}")
            return null
        }
    }

    private fun registerScreenObserver(context: Context): BroadcastReceiver? {
        if (!enabled(context)) return null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(context, "device.broadcast", "action=${intent.action} " +
                    "adapterState=${intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)}")
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        log(context, "device.observer.registered", "lifetime=reactNativeModule noKeepAlive=true")
        return receiver
    }
}
