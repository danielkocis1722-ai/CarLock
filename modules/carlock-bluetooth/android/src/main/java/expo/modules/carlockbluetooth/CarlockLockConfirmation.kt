package expo.modules.carlockbluetooth

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.Tag

internal object CarlockLockConfirmation {
    fun confirmTag(context: Context, tag: Tag): Boolean =
        CarlockVehicleStore.confirmNfc(context, tagId(tag))

    fun tagId(tag: Tag): String = tag.id.joinToString("") { "%02X".format(it) }

    fun cancelReminder(context: Context, vehicleId: String) {
        cancelLegacyReminder(context)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(VehicleReminder.notificationTag(vehicleId), CarlockReminderReceiver.NOTIFICATION_ID)
    }

    fun cancelLegacyReminder(context: Context) {
        // Exact old identity, retained to remove alarms from previous installations.
        val pending = PendingIntent.getBroadcast(context, 2001,
            Intent(context, CarlockReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(CarlockReminderReceiver.NOTIFICATION_ID)
    }
}
