package expo.modules.carlockbluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class CarlockBluetoothModule : Module() {

    private var receiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            // Bluetooth telefónu ON / OFF
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state: Int = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                val enabled =
                    state == BluetoothAdapter.STATE_ON

                Log.d(
                    "CarLockBT",
                    "Bluetooth adapter state=$state enabled=$enabled"
                )

                sendEvent(
                    "onBluetoothStateChanged",
                    bundleOf(
                        "enabled" to enabled
                    )
                )

                return
            }

            val device: BluetoothDevice? =
                getBluetoothDevice(intent)

            val name: String? =
                getDeviceName(device)

            Log.d(
                "CarLockBT",
                "LIVE event=${intent.action}, device=$name"
            )

            // Ignorujeme všetko okrem CITROEN
            if (name?.trim()?.uppercase() != "CITROEN") {
                return
            }

            when (intent.action) {

                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {

                    val state: Int = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED
                    )

                    Log.d(
                        "CarLockBT",
                        "LIVE profile state=$state"
                    )

                    if (
                        state ==
                        BluetoothProfile.STATE_CONNECTED
                    ) {
                        saveConnectionState(true)

                        sendCarEvent(
                            true,
                            device,
                            name
                        )
                    } else if (
                        state ==
                        BluetoothProfile.STATE_DISCONNECTED
                    ) {
                        saveConnectionState(false)

                        sendCarEvent(
                            false,
                            device,
                            name
                        )
                    }
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(
                        "CarLockBT",
                        "LIVE CITROEN CONNECTED"
                    )

                    saveConnectionState(true)

                    sendCarEvent(
                        true,
                        device,
                        name
                    )
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(
                        "CarLockBT",
                        "LIVE CITROEN DISCONNECTED"
                    )

                    saveConnectionState(false)

                    sendCarEvent(
                        false,
                        device,
                        name
                    )
                }
            }
        }
    }

    override fun definition() = ModuleDefinition {
        Name("CarlockBluetooth")

        Events(
            "onCarConnectionChanged",
            "onBluetoothStateChanged"
        )

        //
        // Je Bluetooth telefónu momentálne zapnutý?
        //
        AsyncFunction("isBluetoothEnabled") {
            val adapter =
                BluetoothAdapter.getDefaultAdapter()

            if (adapter == null) {
                return@AsyncFunction false
            }

            return@AsyncFunction try {
                adapter.isEnabled
            } catch (_: SecurityException) {
                false
            }
        }

        //
        // Otvor Android systémový dialóg
        // na zapnutie Bluetooth.
        //
        AsyncFunction("requestEnableBluetooth") {
            val activity =
                appContext.currentActivity
                    ?: return@AsyncFunction false

            val adapter =
                BluetoothAdapter.getDefaultAdapter()
                    ?: return@AsyncFunction false

            try {
                if (adapter.isEnabled) {
                    return@AsyncFunction true
                }

                val enableIntent =
                    Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE
                    )

                activity.startActivity(
                    enableIntent
                )

                Log.d(
                    "CarLockBT",
                    "Requested Bluetooth enable dialog"
                )

                return@AsyncFunction true
            } catch (e: SecurityException) {
                Log.d(
                    "CarLockBT",
                    "Bluetooth enable request failed=${e.message}"
                )

                return@AsyncFunction false
            }
        }

        AsyncFunction("getStoredState") {
            val context =
                appContext.reactContext

            if (context == null) {
                return@AsyncFunction mapOf<String, Any?>(
                    "connected" to false,
                    "locked" to true,
                    "lastEvent" to "NONE",
                    "lastEventAt" to 0L
                )
            }

            val prefs =
                context.getSharedPreferences(
                    "carlock_state",
                    Context.MODE_PRIVATE
                )

            return@AsyncFunction mapOf<String, Any?>(
                "connected" to prefs.getBoolean(
                    "connected",
                    false
                ),
                "locked" to prefs.getBoolean(
                    "locked",
                    true
                ),
                "lastEvent" to prefs.getString(
                    "lastEvent",
                    "NONE"
                ),
                "lastEventAt" to prefs.getLong(
                    "lastEventAt",
                    0L
                )
            )
        }

        AsyncFunction("setLocked") { locked: Boolean ->

            val context =
                appContext.reactContext
                    ?: return@AsyncFunction

            context
                .getSharedPreferences(
                    "carlock_state",
                    Context.MODE_PRIVATE
                )
                .edit()
                .putBoolean(
                    "locked",
                    locked
                )
                .apply()

            Log.d(
                "CarLockBT",
                "Manual locked state=$locked"
            )
        }

        AsyncFunction("isCarConnected") {
            return@AsyncFunction isCitroenConnected()
        }

        OnStartObserving(
            "onCarConnectionChanged"
        ) {
            registerReceiver()
        }

        OnStopObserving(
            "onCarConnectionChanged"
        ) {
            unregisterReceiver()
        }

        OnDestroy {
            unregisterReceiver()
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) {
            return
        }

        val context =
            appContext.reactContext ?: return

        val filter =
            IntentFilter().apply {

                addAction(
                    BluetoothAdapter.ACTION_STATE_CHANGED
                )

                addAction(
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
                )

                addAction(
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED
                )

                addAction(
                    BluetoothDevice.ACTION_ACL_CONNECTED
                )

                addAction(
                    BluetoothDevice.ACTION_ACL_DISCONNECTED
                )
            }

        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        receiverRegistered = true

        Log.d(
            "CarLockBT",
            "LIVE receiver registered"
        )
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) {
            return
        }

        val context =
            appContext.reactContext ?: return

        try {
            context.unregisterReceiver(
                bluetoothReceiver
            )
        } catch (e: Exception) {
            Log.d(
                "CarLockBT",
                "Receiver unregister error=${e.message}"
            )
        }

        receiverRegistered = false
    }

    private fun sendCarEvent(
        connected: Boolean,
        device: BluetoothDevice?,
        name: String?
    ) {
        sendEvent(
            "onCarConnectionChanged",
            bundleOf(
                "connected" to connected,
                "name" to name,
                "address" to device?.address
            )
        )
    }

    private fun saveConnectionState(
        connected: Boolean
    ) {
        val context =
            appContext.reactContext ?: return

        val editor =
            context
                .getSharedPreferences(
                    "carlock_state",
                    Context.MODE_PRIVATE
                )
                .edit()

        editor.putBoolean(
            "connected",
            connected
        )

        editor.putString(
            "lastEvent",
            if (connected) {
                "CONNECTED"
            } else {
                "DISCONNECTED"
            }
        )

        editor.putLong(
            "lastEventAt",
            System.currentTimeMillis()
        )

        // Iba reálny CITROEN connect
        // nastaví auto na UNLOCKED.
        if (connected) {
            editor.putBoolean(
                "locked",
                false
            )
        }

        editor.apply()
    }

    private fun getBluetoothDevice(
        intent: Intent
    ): BluetoothDevice? {
        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE
            )
        }
    }

    private fun getDeviceName(
        device: BluetoothDevice?
    ): String? {
        return try {
            device?.name
        } catch (_: SecurityException) {
            null
        }
    }

    private fun isCitroenConnected(): Boolean {
        val adapter =
            BluetoothAdapter.getDefaultAdapter()
                ?: return false

        return try {
            val a2dp: Int =
                adapter.getProfileConnectionState(
                    BluetoothProfile.A2DP
                )

            val headset: Int =
                adapter.getProfileConnectionState(
                    BluetoothProfile.HEADSET
                )

            val connected: Boolean =
                a2dp ==
                        BluetoothProfile.STATE_CONNECTED ||
                        headset ==
                        BluetoothProfile.STATE_CONNECTED

            Log.d(
                "CarLockBT",
                "Profile check A2DP=$a2dp HEADSET=$headset connected=$connected"
            )

            connected
        } catch (e: SecurityException) {
            false
        }
    }
}