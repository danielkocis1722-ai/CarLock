package expo.modules.carlockbluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.os.bundleOf
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class CarlockBluetoothModule : Module() {

  private var receiverRegistered = false

  private val bluetoothReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent == null) return

      Log.d(
        "CarLockBT",
        "Bluetooth event: ${intent.action}"
      )

      val device: BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java
          )
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

      val name = try {
        device?.name
      } catch (_: SecurityException) {
        null
      }

      Log.d(
        "CarLockBT",
        "Device: $name"
      )

      if (name?.trim()?.uppercase() != "CITROEN") {
        return
      }

      when (intent.action) {

        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {

          val state = intent.getIntExtra(
            BluetoothProfile.EXTRA_STATE,
            BluetoothProfile.STATE_DISCONNECTED
          )

          Log.d(
            "CarLockBT",
            "Profile state: $state"
          )

          sendEvent(
            "onCarConnectionChanged",
            bundleOf(
              "connected" to (state == BluetoothProfile.STATE_CONNECTED),
              "name" to name,
              "address" to device?.address
            )
          )
        }

        BluetoothDevice.ACTION_ACL_CONNECTED -> {
          Log.d(
            "CarLockBT",
            "ACL CONNECTED -> $name"
          )

          sendCarEvent(
            true,
            device,
            name
          )
        }

        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
          Log.d(
            "CarLockBT",
            "ACL DISCONNECTED -> $name"
          )

          sendCarEvent(
            false,
            device,
            name
          )
        }
      }
    }
  }

  override fun definition() = ModuleDefinition {
    Name("CarlockBluetooth")

    Events("onCarConnectionChanged")

    AsyncFunction("isCarConnected") {
      isCitroenConnected()
    }

    OnStartObserving("onCarConnectionChanged") {
      Log.d(
        "CarLockBT",
        "Started observing Bluetooth events"
      )

      registerReceiver()
    }

    OnStopObserving("onCarConnectionChanged") {
      Log.d(
        "CarLockBT",
        "Stopped observing Bluetooth events"
      )

      unregisterReceiver()
    }

    OnDestroy {
      Log.d(
        "CarLockBT",
        "Module destroyed"
      )

      unregisterReceiver()
    }
  }

  private fun sendCarEvent(
    connected: Boolean,
    device: BluetoothDevice?,
    name: String?
  ) {
    Log.d(
      "CarLockBT",
      "Sending event -> connected=$connected, device=$name"
    )

    sendEvent(
      "onCarConnectionChanged",
      bundleOf(
        "connected" to connected,
        "name" to name,
        "address" to device?.address
      )
    )
  }

  private fun registerReceiver() {
    if (receiverRegistered) {
      Log.d(
        "CarLockBT",
        "Receiver already registered"
      )

      return
    }

    val context = appContext.reactContext

    if (context == null) {
      Log.d(
        "CarLockBT",
        "React context is null"
      )

      return
    }

    val filter = IntentFilter().apply {
      addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
      addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
      addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
      addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(
        bluetoothReceiver,
        filter,
        Context.RECEIVER_NOT_EXPORTED
      )
    } else {
      @Suppress("DEPRECATION")
      context.registerReceiver(
        bluetoothReceiver,
        filter
      )
    }

    receiverRegistered = true

    Log.d(
      "CarLockBT",
      "Bluetooth receiver registered"
    )
  }

  private fun unregisterReceiver() {
    if (!receiverRegistered) return

    val context = appContext.reactContext ?: return

    try {
      context.unregisterReceiver(
        bluetoothReceiver
      )

      Log.d(
        "CarLockBT",
        "Bluetooth receiver unregistered"
      )
    } catch (e: Exception) {
      Log.d(
        "CarLockBT",
        "Receiver unregister error: ${e.message}"
      )
    }

    receiverRegistered = false
  }

  private fun isCitroenConnected(): Boolean {
    val adapter = BluetoothAdapter.getDefaultAdapter()

    if (adapter == null) {
      Log.d(
        "CarLockBT",
        "Bluetooth adapter not available"
      )

      return false
    }

    return try {
      val a2dp =
        adapter.getProfileConnectionState(
          BluetoothProfile.A2DP
        )

      val headset =
        adapter.getProfileConnectionState(
          BluetoothProfile.HEADSET
        )

      Log.d(
        "CarLockBT",
        "Initial states -> A2DP: $a2dp, HEADSET: $headset"
      )

      val connected =
        a2dp == BluetoothProfile.STATE_CONNECTED ||
        headset == BluetoothProfile.STATE_CONNECTED

      Log.d(
        "CarLockBT",
        "Initial connected result: $connected"
      )

      connected
    } catch (e: SecurityException) {
      Log.d(
        "CarLockBT",
        "Bluetooth permission error: ${e.message}"
      )

      false
    }
  }
}