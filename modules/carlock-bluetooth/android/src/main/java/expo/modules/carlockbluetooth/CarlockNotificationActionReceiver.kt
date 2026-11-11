package expo.modules.carlockbluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CarlockNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Never fall back to current vehicle for legacy/malformed notification actions.
        val id = intent.getStringExtra("vehicleId") ?: return
        val disconnectAt = intent.getLongExtra("disconnectAt", 0)
        if (disconnectAt <= 0) return
        when (intent.action) {
            ACTION_LOCKED -> CarlockVehicleStore.confirm(context, id, disconnectAt)
            ACTION_KEEP_UNLOCKED -> CarlockVehicleStore.dismiss(context, id, disconnectAt)
        }
    }

    companion object {
        const val ACTION_LOCKED = "expo.modules.carlockbluetooth.ACTION_LOCKED"
        const val ACTION_KEEP_UNLOCKED = "expo.modules.carlockbluetooth.ACTION_KEEP_UNLOCKED"
    }
}
