package expo.modules.carlockbluetooth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class CarlockReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val prefs = context.getSharedPreferences(
            "carlock_state",
            Context.MODE_PRIVATE
        )

        val connected = prefs.getBoolean(
            "connected",
            false
        )

        val locked = prefs.getBoolean(
            "locked",
            true
        )

        // Notification pošleme iba ak:
        // - auto už nie je pripojené
        // - zamknutie stále nebolo potvrdené
        if (connected || locked) {
            return
        }

        createNotificationChannel(context)

        val launchIntent =
            context.packageManager
                .getLaunchIntentForPackage(
                    context.packageName
                )

        val contentIntent =
            if (launchIntent != null) {
                PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_alert
                )
                .setContentTitle(
                    "Did you lock your car?"
                )
                .setContentText(
                    "CITROEN disconnected 30 seconds ago and locking has not been confirmed."
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .apply {
                    if (contentIntent != null) {
                        setContentIntent(
                            contentIntent
                        )
                    }
                }
                .build()

        try {
            NotificationManagerCompat
                .from(context)
                .notify(
                    NOTIFICATION_ID,
                    notification
                )
        } catch (_: SecurityException) {
            // Notification permission nebola udelená.
        }
    }

    private fun createNotificationChannel(
        context: Context
    ) {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Car lock reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                "Reminders when locking has not been confirmed."
        }

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            channel
        )
    }

    companion object {
        const val CHANNEL_ID =
            "carlock_reminders"

        const val NOTIFICATION_ID =
            1001
    }
}