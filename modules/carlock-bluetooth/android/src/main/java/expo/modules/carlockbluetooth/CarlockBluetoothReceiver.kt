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

class CarlockBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        //
        // BLUETOOTH ADAPTER ON / OFF
        //
        if (
            intent.action ==
            BluetoothAdapter.ACTION_STATE_CHANGED
        ) {
            val state =
                intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

            Log.d(
                "CarLockBT",
                "BACKGROUND Bluetooth adapter state=$state"
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

                Log.d(
                    "CarLockBT",
                    "BACKGROUND Bluetooth OFF -> connected=false"
                )
            }

            // Bluetooth OFF samo o sebe
            // nespúšťa lock reminder.
            return
        }

        val device: BluetoothDevice? =
            if (
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

        val name =
            try {
                device?.name
            } catch (_: SecurityException) {
                null
            }

        Log.d(
            "CarLockBT",
            "BACKGROUND action=${intent.action}, device=$name"
        )

        //
        // Ignorujeme JBL, Sony, Buds...
        //
        if (
            name
                ?.trim()
                ?.uppercase() !=
            "CITROEN"
        ) {
            return
        }

        val prefs =
            context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )

        when (intent.action) {

            //
            // CITROEN CONNECTED
            //
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

                //
                // Ak sa auto znovu pripojilo,
                // starý reminder už nechceme.
                //
                cancelLockReminder(
                    context
                )

                Log.d(
                    "CarLockBT",
                    "BACKGROUND CITROEN CONNECTED"
                )
            }

            //
            // CITROEN DISCONNECTED
            //
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

                //
                // UNCONFIRMED
                // → reminder o 30 sekúnd.
                //
                scheduleLockReminder(
                    context
                )

                Log.d(
                    "CarLockBT",
                    "BACKGROUND CITROEN DISCONNECTED -> reminder scheduled"
                )
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {

                val state =
                    intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED
                    )

                Log.d(
                    "CarLockBT",
                    "BACKGROUND profile state=$state"
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


                }
            }
        }
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

        //
        // Nepotrebujeme exact-alarm permission.
        // Reminder nemusí prísť presne na milisekundu.
        //
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )

        Log.d(
            "CarLockBT",
            "Lock reminder scheduled for 30 seconds"
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

        Log.d(
            "CarLockBT",
            "Lock reminder cancelled"
        )
    }

    companion object {
        private const val REMINDER_REQUEST_CODE =
            2001

        private const val REMINDER_DELAY_MS =
            15_000L
    }
}