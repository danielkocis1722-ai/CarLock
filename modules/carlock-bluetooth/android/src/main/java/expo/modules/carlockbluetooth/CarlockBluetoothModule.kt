package expo.modules.carlockbluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class CarlockBluetoothModule : Module() {
    private var observedPrefs: SharedPreferences? = null
    private var receiverRegistered = false
    private val fleetListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == CarlockVehicleStore.STATE_KEY) appContext.reactContext?.let {
            sendEvent("onFleetChanged", bundleOf("stateJson" to CarlockVehicleStore.snapshot(it)))
        }
    }
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            CarlockBluetoothReceiver().onReceive(context, intent)
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                sendEvent("onBluetoothStateChanged", bundleOf("enabled" to
                    (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON)))
            }
        }
    }

    private fun enableNfcReader() {
        val activity = appContext.currentActivity ?: return
        activity.runOnUiThread {
            if (CarlockVehicleStore.read(activity).configuredNfcTags.isEmpty()) return@runOnUiThread
            NfcAdapter.getDefaultAdapter(activity)?.enableReaderMode(activity, { tag ->
                activity.runOnUiThread { CarlockLockConfirmation.confirmTag(activity, tag) }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        }
    }

    private fun disableNfcReader() {
        val activity = appContext.currentActivity ?: return
        activity.runOnUiThread { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }

    override fun definition() = ModuleDefinition {
        Name("CarlockBluetooth")
        Events("onFleetChanged", "onBluetoothStateChanged", "onNfcEnrollmentFinished")

        AsyncFunction("getFleetState") { CarlockVehicleStore.snapshot(requireNotNull(appContext.reactContext)) }
        AsyncFunction("importLegacyProfiles") { raw: String? ->
            CarlockVehicleStore.importProfiles(requireNotNull(appContext.reactContext), raw)
        }
        AsyncFunction("saveVehicle") { id: String, name: String, address: String, bluetoothName: String? ->
            CarlockVehicleStore.saveVehicle(requireNotNull(appContext.reactContext), id, name, address, bluetoothName)
        }
        AsyncFunction("deleteVehicle") { id: String ->
            CarlockVehicleStore.deleteVehicle(requireNotNull(appContext.reactContext), id)
        }
        AsyncFunction("confirmVehicle") { id: String, disconnectAt: Long ->
            val context = requireNotNull(appContext.reactContext)
            CarlockVehicleStore.confirm(context, id, disconnectAt)
            CarlockVehicleStore.snapshot(context)
        }
        AsyncFunction("removeNfcTag") { id: String ->
            val context = requireNotNull(appContext.reactContext)
            val result = CarlockVehicleStore.removeTag(context, id)
            if (CarlockVehicleStore.read(context).configuredNfcTags.isEmpty()) disableNfcReader()
            result
        }
        AsyncFunction("isBluetoothEnabled") {
            try { BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false } catch (_: SecurityException) { false }
        }
        AsyncFunction("requestEnableBluetooth") {
            val activity = appContext.currentActivity ?: return@AsyncFunction false
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@AsyncFunction false
            try {
                if (!adapter.isEnabled) activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                true
            } catch (_: SecurityException) { false }
        }.runOnQueue(Queues.MAIN)
        AsyncFunction("configureNfcTag") {
            val activity = appContext.currentActivity ?: return@AsyncFunction false
            val adapter = NfcAdapter.getDefaultAdapter(activity)
            if (adapter == null || !adapter.isEnabled) return@AsyncFunction false
            activity.startActivityForResult(Intent(activity, CarlockNfcSetupActivity::class.java), 4001)
            true
        }.runOnQueue(Queues.MAIN)
        OnActivityResult { _, result ->
            if (result.requestCode == 4001) sendEvent("onNfcEnrollmentFinished",
                bundleOf("success" to (result.resultCode == Activity.RESULT_OK)))
        }
        OnCreate {
            appContext.reactContext?.let { context ->
                CarlockVehicleStore.read(context)
                observedPrefs = context.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
                observedPrefs?.registerOnSharedPreferenceChangeListener(fleetListener)
                ContextCompat.registerReceiver(context, bluetoothReceiver, IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                }, ContextCompat.RECEIVER_EXPORTED)
                receiverRegistered = true
            }
        }
        OnActivityEntersForeground { enableNfcReader() }
        // The module may be created after the Activity has already resumed.
        OnStartObserving("onFleetChanged") { enableNfcReader() }
        OnActivityEntersBackground { disableNfcReader() }
        OnDestroy {
            disableNfcReader()
            observedPrefs?.unregisterOnSharedPreferenceChangeListener(fleetListener)
            observedPrefs = null
            if (receiverRegistered) appContext.reactContext?.unregisterReceiver(bluetoothReceiver)
            receiverRegistered = false
        }
    }
}
