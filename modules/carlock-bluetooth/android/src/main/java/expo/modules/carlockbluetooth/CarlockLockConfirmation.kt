package expo.modules.carlockbluetooth

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.Tag

internal object CarlockLockConfirmation {
    fun confirm(context: Context) {
        CarlockNfcDiagnostics.log(context, "confirmation.begin")
        context.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
            .edit().putBoolean("locked", true).apply()
        CarlockNfcDiagnostics.log(context, "confirmation.preferences.applied", "diskWrite=async")
        cancelReminder(context)
        CarlockNfcDiagnostics.log(context, "confirmation.complete", "notificationId=1001 cancelled=true")
    }

    fun confirmTag(context: Context, tag: Tag): Boolean {
        val prefs = context.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
        val configured = prefs.getString("nfcTagId", null)
        val matches = !configured.isNullOrBlank() && configured == tagId(tag)
        CarlockNfcDiagnostics.log(context, "confirmation.tag.match", "matches=$matches")
        if (!matches) return false
        confirm(context)
        return true
    }

    fun tagId(tag: Tag): String = tag.id.joinToString("") { "%02X".format(it) }

    fun cancelReminder(context: Context) {
        cancelPendingReminder(context)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(CarlockReminderReceiver.NOTIFICATION_ID)
    }

    fun cancelPendingReminder(context: Context) {
        // Keep the old PendingIntent identity so alarms from an earlier build are removed.
        val pending = PendingIntent.getBroadcast(
            context, 2001, Intent(context, CarlockReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
    }
}
