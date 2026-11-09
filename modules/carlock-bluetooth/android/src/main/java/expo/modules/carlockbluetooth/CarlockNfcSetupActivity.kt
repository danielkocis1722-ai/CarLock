package expo.modules.carlockbluetooth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
        selectedAddress = getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
            .getString("selectedCarAddress", null)
        val vehicleName = getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
            .getString("selectedCarName", "your vehicle")
        setContentView(TextView(this).apply {
            text = "Configure NFC tag for $vehicleName\n\nHold the back of your phone near the NFC tag.\n\nPress Back to cancel."
            setBackgroundColor(Color.rgb(8, 27, 58))
            setTextColor(Color.rgb(234, 244, 255))
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            textSize = 20f
        })
    }

    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null || !adapter.isEnabled || selectedAddress.isNullOrBlank()) {
            Toast.makeText(this, "Select a car and enable NFC first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        adapter.enableReaderMode(this, { tag ->
            runOnUiThread {
                val prefs = getSharedPreferences("carlock_state", Context.MODE_PRIVATE)
                val id = CarlockLockConfirmation.tagId(tag)
                if (!isFinishing && id.isNotBlank() &&
                    prefs.getString("selectedCarAddress", null) == selectedAddress) {
                    prefs.edit().putString("nfcTagId", id).apply()
                    // Require a fresh tap after enrollment, rather than confirming a tag
                    // still held against the phone when the main activity resumes.
                    adapter.ignore(tag, 500, null, null)
                    setResult(RESULT_OK, Intent().putExtra("address", selectedAddress))
                    Toast.makeText(this, "NFC tag configured successfully.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
    }

    override fun onPause() {
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
        super.onPause()
    }
}
