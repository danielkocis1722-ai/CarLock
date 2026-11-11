package expo.modules.carlockbluetooth

import android.app.Activity
import android.graphics.Color
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/** Enrollment appends a global tag and never confirms or selects a vehicle. */
class CarlockNfcSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Add a Locksora NFC tag\n\nHold the back of your phone near the NFC tag.\n\nAny configured tag can confirm the vehicle you just left.\n\nPress Back to cancel."
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
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Enable NFC first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        adapter.enableReaderMode(this, { tag ->
            runOnUiThread {
                val id = CarlockLockConfirmation.tagId(tag)
                if (!isFinishing && id.isNotBlank()) {
                    CarlockVehicleStore.addTag(this, id)
                    // A fresh tap is required for confirmation after enrollment.
                    adapter.ignore(tag, 500, null, null)
                    setResult(RESULT_OK)
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
