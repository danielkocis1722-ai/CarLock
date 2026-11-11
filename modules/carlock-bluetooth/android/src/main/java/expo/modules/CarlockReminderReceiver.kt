package expo.modules.carlockbluetooth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class CarlockReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // An old alarm cannot safely target the new fleet. New reminders are immediate.
        CarlockLockConfirmation.cancelLegacyReminder(context)
    }

    internal fun show(context: Context, vehicle: FleetVehicle) {
        val reminder = vehicle.reminder() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Lock confirmation reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        fun action(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, CarlockNotificationActionReceiver::class.java).apply {
                this.action = action
                // Extras are not part of PendingIntent identity. Data separates vehicles AND trips.
                data = Uri.Builder().scheme("locksora").authority("reminder")
                    .appendPath(reminder.vehicleId).appendPath(reminder.disconnectAt.toString()).appendPath(action).build()
                putExtra("vehicleId", reminder.vehicleId)
                putExtra("disconnectAt", reminder.disconnectAt)
            }
            return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(reminder.title)
            .setContentText("Locksora · Locking has not been confirmed.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_lock_lock, "I locked it",
                action(CarlockNotificationActionReceiver.ACTION_LOCKED, 3001))
            .addAction(android.R.drawable.ic_menu_revert, "Keep unlocked",
                action(CarlockNotificationActionReceiver.ACTION_KEEP_UNLOCKED, 3002))
            .apply {
                if (launch != null) setContentIntent(PendingIntent.getActivity(context, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }.build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.notificationTag, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) { /* Notification permission is controlled by Android. */ }
    }

    companion object {
        const val CHANNEL_ID = "carlock_reminders"
        const val NOTIFICATION_ID = 1001
    }
}
