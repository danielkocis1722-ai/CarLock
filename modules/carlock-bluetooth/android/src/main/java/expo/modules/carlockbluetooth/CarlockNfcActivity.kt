package expo.modules.carlockbluetooth

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle

/** Android can launch this without creating a React Native runtime. */
class CarlockNfcActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleTag(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTag(intent)
    }

    private fun handleTag(intent: Intent) {
        if (intent.action in setOf(
                NfcAdapter.ACTION_TAG_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED,
                NfcAdapter.ACTION_NDEF_DISCOVERED
            )) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) CarlockLockConfirmation.confirmTag(this, tag)
        }
        finish()
    }
}
