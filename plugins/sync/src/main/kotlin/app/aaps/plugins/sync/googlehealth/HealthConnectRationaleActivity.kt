package app.aaps.plugins.sync.googlehealth

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import app.aaps.plugins.sync.R

class HealthConnectRationaleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle(R.string.google_health)
            .setMessage(R.string.google_health_permissions_rationale)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
