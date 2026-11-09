package expo.modules.carlockbluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.functions.Queues

class CarlockBluetoothModule : Module() {

    private var receiverRegistered = false
    private var observedPrefs: SharedPreferences? = null
    private val lockStateListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "locked") {
            sendEvent("onLockStateChanged", bundleOf("locked" to prefs.getBoolean("locked", true)))
        }
    }

    private fun enableNfcReader() {
        val activity = appContext.currentActivity ?: return
        activity.runOnUiThread {
            val prefs = activity.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
            if (prefs.getString("nfcTagId", null).isNullOrBlank()) return@runOnUiThread
            NfcAdapter.getDefaultAdapter(activity)?.enableReaderMode(activity, { tag ->
                activity.runOnUiThread { CarlockLockConfirmation.confirmTag(activity, tag) }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        }
    }

    private fun disableNfcReader() {
        val activity = appContext.currentActivity ?: return
        activity.runOnUiThread {
            NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
        }
    }

    private val bluetoothReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (intent == null) return

                //
                // BLUETOOTH ADAPTER ON / OFF
                //
                if (
                    intent.action ==
                    BluetoothAdapter.ACTION_STATE_CHANGED
                ) {
                    val state =
                        intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR
                        )

                    val enabled =
                        state ==
                                BluetoothAdapter.STATE_ON

                    sendEvent(
                        "onBluetoothStateChanged",
                        bundleOf(
                            "enabled" to enabled
                        )
                    )

                    return
                }

                val device =
                    getBluetoothDevice(
                        intent
                    )

                val name =
                    getDeviceName(
                        device
                    )

                Log.d(
                    "CarLockBT",
                    "LIVE event=${intent.action}, device=$name, address=${device?.address}"
                )

                //
                // NOVÉ:
                // kontrolujeme aktuálne
                // vybrané auto
                //
                if (
                    !isSelectedCar(
                        device,
                        name
                    )
                ) {
                    return
                }

                when (intent.action) {

                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {

                        val state =
                            intent.getIntExtra(
                                BluetoothProfile.EXTRA_STATE,
                                BluetoothProfile.STATE_DISCONNECTED
                            )

                        if (
                            state ==
                            BluetoothProfile.STATE_CONNECTED
                        ) {
                            saveConnectionState(
                                true
                            )

                            sendCarEvent(
                                true,
                                device,
                                name
                            )
                        } else if (
                            state ==
                            BluetoothProfile.STATE_DISCONNECTED
                        ) {
                            saveConnectionState(
                                false
                            )

                            sendCarEvent(
                                false,
                                device,
                                name
                            )
                        }
                    }

                    BluetoothDevice.ACTION_ACL_CONNECTED -> {

                        Log.d(
                            "CarLockBT",
                            "LIVE SELECTED CAR CONNECTED"
                        )

                        saveConnectionState(
                            true
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
                            "LIVE SELECTED CAR DISCONNECTED"
                        )

                        saveConnectionState(
                            false
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

    override fun definition() =
        ModuleDefinition {

            Name(
                "CarlockBluetooth"
            )

            Events(
                "onCarConnectionChanged",
                "onBluetoothStateChanged",
                "onLockStateChanged",
                "onNfcEnrollmentFinished"
            )

            //
            // GET SELECTED CAR
            //
            AsyncFunction(
                "getSelectedCar"
            ) {
                val context =
                    appContext.reactContext

                if (
                    context == null
                ) {
                    return@AsyncFunction mapOf<String, Any?>(
                        "name" to null,
                        "address" to null
                    )
                }

                val prefs =
                    context.getSharedPreferences(
                        "carlock_state",
                        Context.MODE_PRIVATE
                    )

                return@AsyncFunction mapOf<String, Any?>(
                    "name" to
                            prefs.getString(
                                "selectedCarName",
                                null
                            ),

                    "address" to
                            prefs.getString(
                                "selectedCarAddress",
                                null
                            )
                )
            }

            //
            // SET SELECTED CAR
            //
            AsyncFunction(
                "setSelectedCar"
            ) { name: String,
                address: String ->

                val context =
                    appContext.reactContext
                        ?: return@AsyncFunction

                context
                    .getSharedPreferences(
                        "carlock_state",
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .remove("nfcTagId")
                    .putString(
                        "selectedCarName",
                        name
                    )
                    .putString(
                        "selectedCarAddress",
                        address
                    )

                    //
                    // Pri zmene auta
                    // začíname v bezpečnom
                    // neutrálnom stave.
                    //
                    .putBoolean(
                        "connected",
                        false
                    )
                    .putBoolean(
                        "locked",
                        true
                    )
                    .putString(
                        "lastEvent",
                        "NONE"
                    )
                    .putLong(
                        "lastEventAt",
                        0L
                    )
                    .apply()

                cancelLockReminder(
                    context
                )

                Log.d(
                    "CarLockBT",
                    "Selected car changed: $name / $address"
                )
            }

            //
            // BLUETOOTH ON?
            //
            AsyncFunction(
                "isBluetoothEnabled"
            ) {
                val adapter =
                    BluetoothAdapter
                        .getDefaultAdapter()

                if (
                    adapter == null
                ) {
                    return@AsyncFunction false
                }

                return@AsyncFunction try {
                    adapter.isEnabled
                } catch (
                    _: SecurityException
                ) {
                    false
                }
            }

            //
            // ANDROID BLUETOOTH DIALOG
            //
            AsyncFunction(
                "requestEnableBluetooth"
            ) {
                val activity =
                    appContext.currentActivity
                        ?: return@AsyncFunction false

                val adapter =
                    BluetoothAdapter
                        .getDefaultAdapter()
                        ?: return@AsyncFunction false

                try {
                    if (
                        adapter.isEnabled
                    ) {
                        return@AsyncFunction true
                    }

                    val enableIntent =
                        Intent(
                            BluetoothAdapter.ACTION_REQUEST_ENABLE
                        )

                    activity.startActivity(
                        enableIntent
                    )

                    return@AsyncFunction true

                } catch (
                    e: SecurityException
                ) {
                    Log.d(
                        "CarLockBT",
                        "Bluetooth enable error=${e.message}"
                    )

                    return@AsyncFunction false
                }
            }

            //
            // STORED STATE
            //
            AsyncFunction(
                "getStoredState"
            ) {
                val context =
                    appContext.reactContext

                if (
                    context == null
                ) {
                    return@AsyncFunction mapOf<String, Any?>(
                        "connected" to false,
                        "locked" to true,
                        "lastEvent" to "NONE",
                        "lastEventAt" to 0L
                    )
                }

                val prefs =
                    context.getSharedPreferences(
                        "carlock_state",
                        Context.MODE_PRIVATE
                    )

                return@AsyncFunction mapOf<String, Any?>(
                    "connected" to
                            prefs.getBoolean(
                                "connected",
                                false
                            ),

                    "locked" to
                            prefs.getBoolean(
                                "locked",
                                true
                            ),

                    "lastEvent" to
                            prefs.getString(
                                "lastEvent",
                                "NONE"
                            ),

                    "lastEventAt" to
                            prefs.getLong(
                                "lastEventAt",
                                0L
                            )
                )
            }

            //
            // MANUAL LOCK CONFIRMATION
            //
            AsyncFunction(
                "setLocked"
            ) { locked: Boolean ->

                val context =
                    appContext.reactContext
                        ?: return@AsyncFunction

                if (locked) {
                    CarlockLockConfirmation.confirm(context)
                } else {
                    context.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
                        .edit().putBoolean("locked", false).apply()
                }
            }

            // Restore one active profile atomically; keep the established selection reset policy.
            AsyncFunction("activateVehicle") { name: String, address: String, nfcTagId: String? ->
                require(name.isNotBlank() && address.isNotBlank()) { "A vehicle name and Bluetooth address are required." }
                val context = requireNotNull(appContext.reactContext)
                val prefs = context.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
                val changed = !address.equals(prefs.getString("selectedCarAddress", null), ignoreCase = true)
                val editor = prefs.edit()
                    .putString("selectedCarName", name)
                    .putString("selectedCarAddress", address)
                    .putString("nfcTagId", nfcTagId)
                if (changed) {
                    editor.putBoolean("connected", false)
                        .putBoolean("locked", true)
                        .putString("lastEvent", "NONE")
                        .putLong("lastEventAt", 0L)
                }
                editor.apply()
                if (changed) cancelLockReminder(context)
            }

            AsyncFunction("getNfcTagId") {
                appContext.reactContext?.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
                    ?.getString("nfcTagId", null)
            }

            AsyncFunction("configureNfcTag") {
                val activity = appContext.currentActivity ?: return@AsyncFunction false
                val adapter = NfcAdapter.getDefaultAdapter(activity)
                if (adapter == null || !adapter.isEnabled) return@AsyncFunction false
                activity.startActivityForResult(Intent(activity, CarlockNfcSetupActivity::class.java), 4001)
                true
            }.runOnQueue(Queues.MAIN)

            OnActivityResult { _, result ->
                if (result.requestCode == 4001) {
                    sendEvent("onNfcEnrollmentFinished", bundleOf(
                        "success" to (result.resultCode == Activity.RESULT_OK),
                        "address" to result.data?.getStringExtra("address")
                    ))
                }
            }

            OnCreate {
                observedPrefs = appContext.reactContext
                    ?.getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
                observedPrefs?.registerOnSharedPreferenceChangeListener(lockStateListener)
            }

            OnActivityEntersForeground { enableNfcReader() }
            OnActivityEntersBackground { disableNfcReader() }

            AsyncFunction(
                "isCarConnected"
            ) {
                return@AsyncFunction isSelectedCarConnected()
            }

            OnStartObserving(
                "onCarConnectionChanged"
            ) {
                registerReceiver()
            }

            OnStopObserving(
                "onCarConnectionChanged"
            ) {
                unregisterReceiver()
            }

            OnDestroy {
                disableNfcReader()
                observedPrefs?.unregisterOnSharedPreferenceChangeListener(lockStateListener)
                observedPrefs = null
                unregisterReceiver()
            }
        }

    //
    // JE TOTO VYBRANÉ AUTO?
    //
    private fun isSelectedCar(
        device: BluetoothDevice?,
        deviceName: String?
    ): Boolean {

        val context =
            appContext.reactContext
                ?: return false

        val prefs =
            context.getSharedPreferences(
                "carlock_state",
                Context.MODE_PRIVATE
            )

        val selectedAddress =
            prefs.getString(
                "selectedCarAddress",
                null
            )

        val selectedName =
            prefs.getString(
                "selectedCarName",
                null
            )

        //
        // Ak máme MAC adresu,
        // tá má prioritu.
        //
        if (
            !selectedAddress.isNullOrBlank()
        ) {
            return device
                ?.address
                ?.equals(
                    selectedAddress,
                    ignoreCase = true
                ) == true
        }

        //
        // Fallback pre existujúcu
        // inštaláciu pred Settings.
        //
        val nameToMatch =
            selectedName
                ?: "CITROEN"

        return deviceName
            ?.trim()
            ?.equals(
                nameToMatch.trim(),
                ignoreCase = true
            ) == true
    }

    private fun registerReceiver() {
        if (
            receiverRegistered
        ) {
            return
        }

        val context =
            appContext.reactContext
                ?: return

        val filter =
            IntentFilter().apply {

                addAction(
                    BluetoothAdapter.ACTION_STATE_CHANGED
                )

                addAction(
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
                )

                addAction(
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED
                )

                addAction(
                    BluetoothDevice.ACTION_ACL_CONNECTED
                )

                addAction(
                    BluetoothDevice.ACTION_ACL_DISCONNECTED
                )
            }

        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        enableNfcReader()

        receiverRegistered =
            true
    }

    private fun unregisterReceiver() {
        if (
            !receiverRegistered
        ) {
            return
        }

        val context =
            appContext.reactContext
                ?: return

        try {
            context.unregisterReceiver(
                bluetoothReceiver
            )
        } catch (
            _: Exception
        ) {
        }

        receiverRegistered =
            false
    }

    private fun sendCarEvent(
        connected: Boolean,
        device: BluetoothDevice?,
        name: String?
    ) {
        sendEvent(
            "onCarConnectionChanged",
            bundleOf(
                "connected" to connected,
                "name" to name,
                "address" to
                        device?.address
            )
        )
    }

    private fun saveConnectionState(
        connected: Boolean
    ) {
        val context =
            appContext.reactContext
                ?: return

        val editor =
            context
                .getSharedPreferences(
                    "carlock_state",
                    Context.MODE_PRIVATE
                )
                .edit()

        editor.putBoolean(
            "connected",
            connected
        )

        editor.putString(
            "lastEvent",
            if (
                connected
            ) {
                "CONNECTED"
            } else {
                "DISCONNECTED"
            }
        )

        editor.putLong(
            "lastEventAt",
            System.currentTimeMillis()
        )

        if (
            connected
        ) {
            editor.putBoolean(
                "locked",
                false
            )
        }

        editor.apply()
        if (connected) {
            CarlockLockConfirmation.cancelReminder(context)
        } else {
            CarlockLockConfirmation.cancelPendingReminder(context)
            CarlockReminderReceiver().show(context)
        }
    }

    private fun getBluetoothDevice(
        intent: Intent
    ): BluetoothDevice? {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            @Suppress(
                "DEPRECATION"
            )
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE
            )
        }
    }

    private fun getDeviceName(
        device: BluetoothDevice?
    ): String? {

        return try {
            device?.name
        } catch (
            _: SecurityException
        ) {
            null
        }
    }

    private fun cancelLockReminder(
        context: Context
    ) {
        CarlockLockConfirmation.cancelReminder(context)
    }

    private fun isSelectedCarConnected(): Boolean {

        //
        // Zatiaľ túto funkciu
        // nepoužívame ako source of truth.
        // Stored events ostávajú hlavné.
        //
        val adapter =
            BluetoothAdapter
                .getDefaultAdapter()
                ?: return false

        return try {
            val a2dp =
                adapter
                    .getProfileConnectionState(
                        BluetoothProfile.A2DP
                    )

            val headset =
                adapter
                    .getProfileConnectionState(
                        BluetoothProfile.HEADSET
                    )

            a2dp ==
                    BluetoothProfile.STATE_CONNECTED ||
                    headset ==
                    BluetoothProfile.STATE_CONNECTED

        } catch (
            _: SecurityException
        ) {
            false
        }
    }
}
