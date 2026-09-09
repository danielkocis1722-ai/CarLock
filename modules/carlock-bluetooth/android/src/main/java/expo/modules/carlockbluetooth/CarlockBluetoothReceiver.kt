package expo.modules.carlockbluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.bluetooth.BluetoothAdapter
import android.util.Log

class CarlockBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR
            )

            Log.d(
                "CarLockBT",
                "BACKGROUND Bluetooth adapter state=$state"
            )

            if (state == BluetoothAdapter.STATE_OFF) {
                context
                    .getSharedPreferences(
                        "carlock_state",
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putBoolean("connected", false)
                    .apply()

                Log.d(
                    "CarLockBT",
                    "BACKGROUND Bluetooth OFF -> connected=false"
                )
            }

            // LOCKED / UNLOCKED nemeníme.
            return
        }

        val device: BluetoothDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

        val name = try {
            device?.name
        } catch (_: SecurityException) {
            null
        }

        Log.d(
            "CarLockBT",
            "BACKGROUND action=${intent.action}, device=$name"
        )

        if (name?.trim()?.uppercase() != "CITROEN") {
            return
        }

        val prefs = context.getSharedPreferences(
            "carlock_state",
            Context.MODE_PRIVATE
        )

        when (intent.action) {

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                prefs.edit()
                    .putBoolean("connected", true)
                    .putBoolean("locked", false)
                    .putString("lastEvent", "CONNECTED")
                    .putLong("lastEventAt", System.currentTimeMillis())
                    .apply()

                Log.d("CarLockBT", "BACKGROUND CITROEN CONNECTED")
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                prefs.edit()
                    .putBoolean("connected", false)
                    // locked nechávame false
                    .putString("lastEvent", "DISCONNECTED")
                    .putLong("lastEventAt", System.currentTimeMillis())
                    .apply()

                Log.d("CarLockBT", "BACKGROUND CITROEN DISCONNECTED")
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {

                val state = intent.getIntExtra(
                    BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_DISCONNECTED
                )

                Log.d(
                    "CarLockBT",
                    "BACKGROUND profile state=$state"
                )

                if (state == BluetoothProfile.STATE_CONNECTED) {
                    prefs.edit()
                        .putBoolean("connected", true)
                        .putBoolean("locked", false)
                        .putString("lastEvent", "CONNECTED")
                        .putLong("lastEventAt", System.currentTimeMillis())
                        .apply()
                }

                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    prefs.edit()
                        .putBoolean("connected", false)
                        .putString("lastEvent", "DISCONNECTED")
                        .putLong("lastEventAt", System.currentTimeMillis())
                        .apply()
                }
            }
        }
    }
}