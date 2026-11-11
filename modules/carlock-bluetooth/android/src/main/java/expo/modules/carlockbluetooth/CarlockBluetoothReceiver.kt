package expo.modules.carlockbluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Shared by manifest delivery and the live module; duplicate transitions are idempotent. */
class CarlockBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_ON) {
                CarlockVehicleStore.adapterState(context, state == BluetoothAdapter.STATE_ON)
            }
            return
        }
        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> when (
                intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            ) {
                BluetoothProfile.STATE_CONNECTED -> true
                BluetoothProfile.STATE_DISCONNECTED -> false
                else -> return
            }
            else -> return
        }
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = try { device?.address } catch (_: SecurityException) { null } ?: return
        CarlockVehicleStore.bluetooth(context, address, connected)
    }
}
