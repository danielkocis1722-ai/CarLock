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

            // Bluetooth adaptér ON/OFF nemá konkrétne device.
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                Log.d(
                    "CarLockBT",
                    "Bluetooth adapter state=$state"
                )

                sendEvent(
                    "onBluetoothStateChanged",
                    bundleOf(
                        "enabled" to (state == BluetoothAdapter.STATE_ON)
                    )
                )

                return
            }

            val device = getBluetoothDevice(intent)
            val name = getDeviceName(device)

            Log.d(
                "CarLockBT",
                "LIVE event=${intent.action}, device=$name"
            )

            if (name?.trim()?.uppercase() != "CITROEN") {
                return
            }

            when (intent.action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    // tvoj existujúci kód...
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    saveConnectionState(true)
                    sendCarEvent(true, device, name)
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    saveConnectionState(false)
                    sendCarEvent(false, device, name)
                }
            }
        }

        override fun definition() = ModuleDefinition {
            Name("CarlockBluetooth")

            Events(
                "onCarConnectionChanged",
                "onBluetoothStateChanged"
            )

            AsyncFunction("getStoredState") {
                val context = appContext.reactContext
                    ?: return@AsyncFunction mapOf(
                        "connected" to false,
                        "locked" to true,
                        "lastEvent" to "NONE",
                        "lastEventAt" to 0L
                    )

                val prefs = context.getSharedPreferences(
                    "carlock_state",
                    Context.MODE_PRIVATE
                )

                mapOf(
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
                val context = appContext.reactContext
                    ?: return@AsyncFunction

                context
                    .getSharedPreferences(
                        "carlock_state",
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putBoolean("locked", locked)
                    .apply()

                Log.d(
                    "CarLockBT",
                    "Manual locked state=$locked"
                )
            }

            AsyncFunction("isCarConnected") {
                isCitroenConnected()
            }

            OnStartObserving("onCarConnectionChanged") {
                registerReceiver()
            }

            OnStopObserving("onCarConnectionChanged") {
                unregisterReceiver()
            }

            OnDestroy {
                unregisterReceiver()
            }
        }

        private fun registerReceiver() {
            if (receiverRegistered) return

            val context = appContext.reactContext ?: return

            val filter = IntentFilter().apply {
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
            if (!receiverRegistered) return

            val context = appContext.reactContext ?: return

            try {
                context.unregisterReceiver(
                    bluetoothReceiver
                )
            } catch (_: Exception) {
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

            val editor = context
                .getSharedPreferences(
                    "carlock_state",
                    Context.MODE_PRIVATE
                )
                .edit()

            editor.putBoolean(
                "connected",
                connected
            )

            // Keď sa auto pripojí,
            // vieme, že ho používame.
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
                val a2dp =
                    adapter.getProfileConnectionState(
                        BluetoothProfile.A2DP
                    )

                val headset =
                    adapter.getProfileConnectionState(
                        BluetoothProfile.HEADSET
                    )

                val connected =
                    a2dp ==
                            BluetoothProfile.STATE_CONNECTED ||
                            headset ==
                            BluetoothProfile.STATE_CONNECTED

                Log.d(
                    "CarLockBT",
                    "Initial A2DP=$a2dp HEADSET=$headset connected=$connected"
                )

                connected
            } catch (_: SecurityException) {
                false
            }
        }
    }
}