package expo.modules.carlockbluetooth

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/** Private enrollment screen: a normal tag scan can never change the configured tag. */
class CarlockNfcSetupActivity : Activity() {
    private var selectedAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CarlockNfcDiagnostics.log(this, "setup.activity.onCreate")
        selectedAddress = getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
            .getString("selectedCarAddress", null)
        setContentView(TextView(this).apply {
            text = "Hold your lock confirmation NFC tag near the phone.\n\nPress Back to cancel."
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            textSize = 20f
        })
    }

    override fun onResume() {
        super.onResume()
        CarlockNfcDiagnostics.log(this, "setup.activity.onResume")
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null || !adapter.isEnabled || selectedAddress.isNullOrBlank()) {
            CarlockNfcDiagnostics.log(this, "setup.reader.skipped", "reason=missingAdapterOrDisabledOrNoCar")
            Toast.makeText(this, "Select a car and enable NFC first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        CarlockNfcDiagnostics.log(this, "setup.reader.enable.request")
        adapter.enableReaderMode(this, { tag ->
            CarlockNfcDiagnostics.tag(this, "setup.reader.callback", tag)
            runOnUiThread {
                val prefs = getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
                val id = CarlockLockConfirmation.tagId(tag)
                if (!isFinishing && id.isNotBlank() &&
                    prefs.getString("selectedCarAddress", null) == selectedAddress) {
                    prefs.edit().putString("nfcTagId", id).apply()
                    CarlockNfcDiagnostics.log(this, "setup.tag.saved", "lockStateUnchanged=true")
                    Toast.makeText(this, "NFC lock tag configured.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        CarlockNfcDiagnostics.log(this, "setup.reader.enable.returned", "doesNotProvePolling=true")
    }

    override fun onPause() {
        CarlockNfcDiagnostics.log(this, "setup.activity.onPause")
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
        super.onPause()
    }
}
