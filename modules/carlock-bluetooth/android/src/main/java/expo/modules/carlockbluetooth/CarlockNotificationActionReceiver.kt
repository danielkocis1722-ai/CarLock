package expo.modules.carlockbluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CarlockNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {

            ACTION_LOCKED -> {
                //
                // Používateľ potvrdil,
                // že auto zamkol.
                //
                CarlockLockConfirmation.confirm(context)

                Log.d(
                    "CarLockBT",
                    "Notification action: I LOCKED IT"
                )

            }

            ACTION_KEEP_UNLOCKED -> {
                //
                // Auto ostáva UNLOCKED.
                // Nič nemeníme v locked=false.
                //
                Log.d(
                    "CarLockBT",
                    "Notification action: KEEP UNLOCKED"
                )

                CarlockLockConfirmation.cancelReminder(context)
            }
        }
    }

    companion object {
        const val ACTION_LOCKED =
            "expo.modules.carlockbluetooth.ACTION_LOCKED"

        const val ACTION_KEEP_UNLOCKED =
            "expo.modules.carlockbluetooth.ACTION_KEEP_UNLOCKED"
    }
}
