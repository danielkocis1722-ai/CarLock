package expo.modules.carlockbluetooth

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class CarlockBluetoothReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action ==
            BluetoothAdapter.ACTION_STATE_CHANGED
        ) {
            val state =
                intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

            if (
                state ==
                BluetoothAdapter.STATE_OFF
            ) {
                context
                    .getSharedPreferences(
                        "carlock_state",
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putBoolean(
                        "connected",
                        false
                    )
                    .apply()
            }

            return
        }

        val device =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
            } else {
                @Suppress(
                    "DEPRECATION"
                )
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE
                )
            }

        val name =
            try {
                device?.name
            } catch (
                _: SecurityException
            ) {
                null
            }

        Log.d(
            "CarLockBT",
            "BACKGROUND event=${intent.action}, device=$name, address=${device?.address}"
        )

        //
        // NOVÉ:
        // filtrujeme selected car
        //
        if (
            !isSelectedCar(
                context,
                device,
                name
            )
        ) {
            return
        }

        val prefs =
            context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )

        when (
            intent.action
        ) {

            BluetoothDevice.ACTION_ACL_CONNECTED -> {

                prefs.edit()
                    .putBoolean(
                        "connected",
                        true
                    )
                    .putBoolean(
                        "locked",
                        false
                    )
                    .putString(
                        "lastEvent",
                        "CONNECTED"
                    )
                    .putLong(
                        "lastEventAt",
                        System.currentTimeMillis()
                    )
                    .apply()

                cancelLockReminder(
                    context
                )

                Log.d(
                    "CarLockBT",
                    "BACKGROUND SELECTED CAR CONNECTED"
                )
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {

                prefs.edit()
                    .putBoolean(
                        "connected",
                        false
                    )
                    .putString(
                        "lastEvent",
                        "DISCONNECTED"
                    )
                    .putLong(
                        "lastEventAt",
                        System.currentTimeMillis()
                    )
                    .apply()

                scheduleLockReminder(
                    context
                )

                Log.d(
                    "CarLockBT",
                    "BACKGROUND SELECTED CAR DISCONNECTED -> reminder scheduled"
                )
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {

                val state =
                    intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED
                    )

                if (
                    state ==
                    BluetoothProfile.STATE_CONNECTED
                ) {
                    prefs.edit()
                        .putBoolean(
                            "connected",
                            true
                        )
                        .putBoolean(
                            "locked",
                            false
                        )
                        .putString(
                            "lastEvent",
                            "CONNECTED"
                        )
                        .putLong(
                            "lastEventAt",
                            System.currentTimeMillis()
                        )
                        .apply()

                    cancelLockReminder(
                        context
                    )
                }

                if (
                    state ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {
                    prefs.edit()
                        .putBoolean(
                            "connected",
                            false
                        )
                        .putString(
                            "lastEvent",
                            "DISCONNECTED"
                        )
                        .putLong(
                            "lastEventAt",
                            System.currentTimeMillis()
                        )
                        .apply()

                    //
                    // Reminder tu nedávame.
                    // Čakáme na ACL disconnect.
                    //
                }
            }
        }
    }

    private fun isSelectedCar(
        context: Context,
        device: BluetoothDevice?,
        deviceName: String?
    ): Boolean {

        val prefs =
            context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )

        val selectedAddress =
            prefs.getString(
                "selectedCarAddress",
                null
            )

        val selectedName =
            prefs.getString(
                "selectedCarName",
                null
            )

        if (
            !selectedAddress.isNullOrBlank()
        ) {
            return device
                ?.address
                ?.equals(
                    selectedAddress,
                    ignoreCase = true
                ) == true
        }

        //
        // Migration fallback:
        // kým si nič nevybral,
        // sleduj CITROEN.
        //
        val nameToMatch =
            selectedName
                ?: "CITROEN"

        return deviceName
            ?.trim()
            ?.equals(
                nameToMatch.trim(),
                ignoreCase = true
            ) == true
    }

    private fun scheduleLockReminder(
        context: Context
    ) {
        val alarmManager =
            context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

        val intent =
            Intent(
                context,
                CarlockReminderReceiver::class.java
            )

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val triggerAt =
            System.currentTimeMillis() +
                    REMINDER_DELAY_MS

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )

        Log.d(
            "CarLockBT",
            "Lock reminder scheduled for 15 seconds"
        )
    }

    private fun cancelLockReminder(
        context: Context
    ) {
        val alarmManager =
            context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

        val intent =
            Intent(
                context,
                CarlockReminderReceiver::class.java
            )

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.cancel(
            pendingIntent
        )
    }

    companion object {

        private const val
                REMINDER_REQUEST_CODE =
            2001

        private const val
                REMINDER_DELAY_MS =
            15_000L
    }
}