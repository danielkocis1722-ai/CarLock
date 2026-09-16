package expo.modules.carlockbluetooth

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CarlockNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val prefs =
            context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )

        when (intent.action) {

            ACTION_LOCKED -> {
                prefs.edit()
                    .putBoolean(
                        "locked",
                        true
                    )
                    .apply()

                notifyLockStateChanged(
                    context,
                    true
                )

                Log.d(
                    "CarLockBT",
                    "Notification action: I LOCKED IT"
                )

                dismissNotification(
                    context
                )
            }

            ACTION_KEEP_UNLOCKED -> {
                prefs.edit()
                    .putBoolean(
                        "locked",
                        false
                    )
                    .apply()

                notifyLockStateChanged(
                    context,
                    false
                )

                Log.d(
                    "CarLockBT",
                    "Notification action: KEEP UNLOCKED"
                )

                dismissNotification(
                    context
                )
            }
        }
    }

    private fun notifyLockStateChanged(
        context: Context,
        locked: Boolean
    ) {
        val stateIntent =
            Intent(
                CarlockBluetoothModule.ACTION_LOCK_STATE_CHANGED
            ).apply {
                setPackage(
                    context.packageName
                )
                putExtra(
                    CarlockBluetoothModule.EXTRA_LOCKED,
                    locked
                )
            }

        context.sendBroadcast(
            stateIntent
        )
    }

    private fun dismissNotification(
        context: Context
    ) {
        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.cancel(
            CarlockReminderReceiver.NOTIFICATION_ID
        )
    }

    companion object {
        const val ACTION_LOCKED =
            "expo.modules.carlockbluetooth.ACTION_LOCKED"

        const val ACTION_KEEP_UNLOCKED =
            "expo.modules.carlockbluetooth.ACTION_KEEP_UNLOCKED"
    }
}
