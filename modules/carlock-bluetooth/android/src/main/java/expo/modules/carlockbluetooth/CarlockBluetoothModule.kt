package expo.modules.carlockbluetooth

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

            if (intent.action == ACTION_LOCK_STATE_CHANGED) {
                val locked = intent.getBooleanExtra(EXTRA_LOCKED, false)

                sendEvent(
                    "onLockStateChanged",
                    bundleOf("locked" to locked)
                )

                Log.d("CarLockBT", "LIVE lock state changed=$locked")
                return
            }

            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                val enabled = state == BluetoothAdapter.STATE_ON

                sendEvent(
                    "onBluetoothStateChanged",
                    bundleOf("enabled" to enabled)
                )

                return
            }

            val device = getBluetoothDevice(intent)
            val name = getDeviceName(device)

            Log.d(
                "CarLockBT",
                "LIVE event=${intent.action}, device=$name, address=${device?.address}"
            )

            if (!isSelectedCar(device, name)) {
                return
            }

            when (intent.action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED
                    )

                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        saveConnectionState(true)
                        sendCarEvent(true, device, name)
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        saveConnectionState(false)
                        sendCarEvent(false, device, name)
                    }
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    saveConnectionState(true)
                    sendCarEvent(true, device, name)
                    Log.d("CarLockBT", "LIVE SELECTED CAR CONNECTED")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    saveConnectionState(false)
                    sendCarEvent(false, device, name)
                    Log.d("CarLockBT", "LIVE SELECTED CAR DISCONNECTED")
                }
            }
        }
    }

    override fun definition() = ModuleDefinition {
        Name("CarlockBluetooth")

        Events(
            "onCarConnectionChanged",
            "onBluetoothStateChanged",
            "onLockStateChanged"
        )

        AsyncFunction("getSelectedCar") {
            val context = appContext.reactContext

            if (context == null) {
                return@AsyncFunction mapOf<String, Any?>(
                    "name" to null,
                    "address" to null
                )
            }

            val prefs = context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )

            return@AsyncFunction mapOf<String, Any?>(
                "name" to prefs.getString("selectedCarName", null),
                "address" to prefs.getString("selectedCarAddress", null)
            )
        }

        AsyncFunction("setSelectedCar") { name: String, address: String ->
            val context = appContext.reactContext ?: return@AsyncFunction

            context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )
                .edit()
                .putString("selectedCarName", name)
                .putString("selectedCarAddress", address)
                .putBoolean("connected", false)
                .putBoolean("locked", true)
                .putString("lastEvent", "NONE")
                .putLong("lastEventAt", 0L)
                .apply()

            cancelLockReminder(context)

            Log.d("CarLockBT", "Selected car changed: $name / $address")
        }

        AsyncFunction("isBluetoothEnabled") {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@AsyncFunction false

            return@AsyncFunction try {
                adapter.isEnabled
            } catch (_: SecurityException) {
                false
            }
        }

        AsyncFunction("requestEnableBluetooth") {
            val activity = appContext.currentActivity
                ?: return@AsyncFunction false

            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@AsyncFunction false

            try {
                if (adapter.isEnabled) {
                    return@AsyncFunction true
                }

                activity.startActivity(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )

                return@AsyncFunction true
            } catch (e: SecurityException) {
                Log.d("CarLockBT", "Bluetooth enable error=${e.message}")
                return@AsyncFunction false
            }
        }

        AsyncFunction("canScheduleExactAlarms") {
            val context = appContext.reactContext
                ?: return@AsyncFunction false

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return@AsyncFunction true
            }

            val alarmManager = context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

            return@AsyncFunction alarmManager.canScheduleExactAlarms()
        }

        AsyncFunction("requestExactAlarmPermission") {
            val context = appContext.reactContext
                ?: return@AsyncFunction false

            val activity = appContext.currentActivity
                ?: return@AsyncFunction false

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return@AsyncFunction true
            }

            val alarmManager = context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

            if (alarmManager.canScheduleExactAlarms()) {
                return@AsyncFunction true
            }

            return@AsyncFunction try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                )

                activity.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.d("CarLockBT", "Exact alarm settings error=${e.message}")
                false
            }
        }

        AsyncFunction("getStoredState") {
            val context = appContext.reactContext

            if (context == null) {
                return@AsyncFunction mapOf<String, Any?>(
                    "connected" to false,
                    "locked" to true,
                    "lastEvent" to "NONE",
                    "lastEventAt" to 0L
                )
            }

            val prefs = context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )

            return@AsyncFunction mapOf<String, Any?>(
                "connected" to prefs.getBoolean("connected", false),
                "locked" to prefs.getBoolean("locked", true),
                "lastEvent" to prefs.getString("lastEvent", "NONE"),
                "lastEventAt" to prefs.getLong("lastEventAt", 0L)
            )
        }

        AsyncFunction("setLocked") { locked: Boolean ->
            val context = appContext.reactContext ?: return@AsyncFunction

            context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )
                .edit()
                .putBoolean("locked", locked)
                .apply()

            if (locked) {
                cancelLockReminder(context)
            }

            sendEvent(
                "onLockStateChanged",
                bundleOf("locked" to locked)
            )

            Log.d("CarLockBT", "Manual locked state=$locked")
        }

        AsyncFunction("isCarConnected") {
            return@AsyncFunction isSelectedCarConnected()
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

    private fun isSelectedCar(
        device: BluetoothDevice?,
        deviceName: String?
    ): Boolean {
        val context = appContext.reactContext ?: return false

        val prefs = context.getSharedPreferences(
            "carlock_state",
            Context.MODE_PRIVATE
        )

        val selectedAddress = prefs.getString(
            "selectedCarAddress",
            null
        )

        val selectedName = prefs.getString(
            "selectedCarName",
            null
        )

        if (!selectedAddress.isNullOrBlank()) {
            return device?.address?.equals(
                selectedAddress,
                ignoreCase = true
            ) == true
        }

        val nameToMatch = selectedName ?: "CITROEN"

        return deviceName
            ?.trim()
            ?.equals(
                nameToMatch.trim(),
                ignoreCase = true
            ) == true
    }

    private fun registerReceiver() {
        if (receiverRegistered) return

        val context = appContext.reactContext ?: return

        val filter = IntentFilter().apply {
            addAction(ACTION_LOCK_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return

        val context = appContext.reactContext ?: return

        try {
            context.unregisterReceiver(bluetoothReceiver)
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
        val context = appContext.reactContext ?: return

        val editor = context.getSharedPreferences(
            "carlock_state",
            Context.MODE_PRIVATE
        ).edit()

        editor.putBoolean("connected", connected)
        editor.putString(
            "lastEvent",
            if (connected) "CONNECTED" else "DISCONNECTED"
        )
        editor.putLong("lastEventAt", System.currentTimeMillis())

        if (connected) {
            editor.putBoolean("locked", false)
        }

        editor.apply()
    }

    private fun getBluetoothDevice(
        intent: Intent
    ): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
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

    private fun cancelLockReminder(
        context: Context
    ) {
        val alarmManager = context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        val intent = Intent(
            context,
            CarlockReminderReceiver::class.java
        )

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    private fun isSelectedCarConnected(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return false

        return try {
            val a2dp = adapter.getProfileConnectionState(
                BluetoothProfile.A2DP
            )

            val headset = adapter.getProfileConnectionState(
                BluetoothProfile.HEADSET
            )

            a2dp == BluetoothProfile.STATE_CONNECTED ||
                headset == BluetoothProfile.STATE_CONNECTED
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        const val ACTION_LOCK_STATE_CHANGED =
            "expo.modules.carlockbluetooth.ACTION_LOCK_STATE_CHANGED"

        const val EXTRA_LOCKED =
            "expo.modules.carlockbluetooth.EXTRA_LOCKED"
    }
}
