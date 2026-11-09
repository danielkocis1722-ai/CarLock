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
        CarlockNfcDiagnostics.intent(this, "dispatch.activity.onCreate", intent)
        handleTag(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        CarlockNfcDiagnostics.intent(this, "dispatch.activity.onNewIntent", intent)
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
            if (tag != null) {
                CarlockNfcDiagnostics.tag(this, "dispatch.tag.parsed", tag)
                val confirmed = CarlockLockConfirmation.confirmTag(this, tag)
                CarlockNfcDiagnostics.log(this, "dispatch.confirmation.result", "confirmed=$confirmed")
            } else {
                CarlockNfcDiagnostics.log(this, "dispatch.tag.missing", "confirmationSkipped=true")
            }
        } else {
            CarlockNfcDiagnostics.log(this, "dispatch.action.ignored", "action=${intent.action}")
        }
        CarlockNfcDiagnostics.log(this, "dispatch.activity.finish")
        finish()
    }

    override fun onResume() {
        super.onResume()
        CarlockNfcDiagnostics.log(this, "dispatch.activity.onResume")
    }

    override fun onDestroy() {
        CarlockNfcDiagnostics.log(this, "dispatch.activity.onDestroy")
        super.onDestroy()
    }
}
