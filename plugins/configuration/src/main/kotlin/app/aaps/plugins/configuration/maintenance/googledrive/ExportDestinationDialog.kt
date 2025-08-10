package app.aaps.plugins.configuration.maintenance.googledrive

import android.content.Context
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportDestinationDialog @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val googleDriveManager: GoogleDriveManager
) {
    
    companion object {
        // SharedPreferences keys for export destination settings
        const val PREF_LOG_EMAIL_ENABLED = "export_log_email_enabled"
        const val PREF_LOG_CLOUD_ENABLED = "export_log_cloud_enabled"
        const val PREF_SETTINGS_LOCAL_ENABLED = "export_settings_local_enabled"
        const val PREF_SETTINGS_CLOUD_ENABLED = "export_settings_cloud_enabled"
        const val PREF_CSV_LOCAL_ENABLED = "export_csv_local_enabled"
        const val PREF_CSV_CLOUD_ENABLED = "export_csv_cloud_enabled"
    }
    
    /**
     * Show export destination configuration dialog
     */
    fun showExportDestinationDialog(
        activity: DaggerAppCompatActivityWithResult,
        onSettingsChanged: () -> Unit = {}
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_export_destination, null)
        
        val logEmailSwitch = dialogView.findViewById<Switch>(R.id.log_email_switch)
        val logCloudSwitch = dialogView.findViewById<Switch>(R.id.log_cloud_switch)
        val settingsLocalSwitch = dialogView.findViewById<Switch>(R.id.settings_local_switch)
        val settingsCloudSwitch = dialogView.findViewById<Switch>(R.id.settings_cloud_switch)
        val csvLocalSwitch = dialogView.findViewById<Switch>(R.id.csv_local_switch)
        val csvCloudSwitch = dialogView.findViewById<Switch>(R.id.csv_cloud_switch)
        
        // Load current settings
        logEmailSwitch.isChecked = sp.getBoolean(PREF_LOG_EMAIL_ENABLED, true) // Default to email
        logCloudSwitch.isChecked = sp.getBoolean(PREF_LOG_CLOUD_ENABLED, false)
        settingsLocalSwitch.isChecked = sp.getBoolean(PREF_SETTINGS_LOCAL_ENABLED, true) // Default to local
        settingsCloudSwitch.isChecked = sp.getBoolean(PREF_SETTINGS_CLOUD_ENABLED, false)
        csvLocalSwitch.isChecked = sp.getBoolean(PREF_CSV_LOCAL_ENABLED, true) // Default to local
        csvCloudSwitch.isChecked = sp.getBoolean(PREF_CSV_CLOUD_ENABLED, false)
        
        // Check if cloud directory is configured
        val isCloudConfigured = googleDriveManager.getStorageType() == GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE
        
        // Disable cloud options if not configured
        if (!isCloudConfigured) {
            logCloudSwitch.isEnabled = false
            settingsCloudSwitch.isEnabled = false
            csvCloudSwitch.isEnabled = false
            
            // Force disable cloud options and enable local/email options
            logCloudSwitch.isChecked = false
            settingsCloudSwitch.isChecked = false
            csvCloudSwitch.isChecked = false
            
            if (!logEmailSwitch.isChecked && !logCloudSwitch.isChecked) {
                logEmailSwitch.isChecked = true
            }
            if (!settingsLocalSwitch.isChecked && !settingsCloudSwitch.isChecked) {
                settingsLocalSwitch.isChecked = true
            }
            if (!csvLocalSwitch.isChecked && !csvCloudSwitch.isChecked) {
                csvLocalSwitch.isChecked = true
            }
        }
        
        // Set up mutual exclusivity for each row
        setupMutualExclusivity(logEmailSwitch, logCloudSwitch)
        setupMutualExclusivity(settingsLocalSwitch, settingsCloudSwitch)
        setupMutualExclusivity(csvLocalSwitch, csvCloudSwitch)
        
        val dialog = AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.export_destination))
            .setView(dialogView)
            .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.ok)) { _, _ ->
                // Save settings
                sp.putBoolean(PREF_LOG_EMAIL_ENABLED, logEmailSwitch.isChecked)
                sp.putBoolean(PREF_LOG_CLOUD_ENABLED, logCloudSwitch.isChecked)
                sp.putBoolean(PREF_SETTINGS_LOCAL_ENABLED, settingsLocalSwitch.isChecked)
                sp.putBoolean(PREF_SETTINGS_CLOUD_ENABLED, settingsCloudSwitch.isChecked)
                sp.putBoolean(PREF_CSV_LOCAL_ENABLED, csvLocalSwitch.isChecked)
                sp.putBoolean(PREF_CSV_CLOUD_ENABLED, csvCloudSwitch.isChecked)
                
                onSettingsChanged()
                ToastUtils.infoToast(activity, "匯出目的地設定已更新")
            }
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .create()
        
        dialog.show()
    }
    
    /**
     * Set up mutual exclusivity between two switches - when one is turned on, the other is turned off
     */
    private fun setupMutualExclusivity(switch1: Switch, switch2: Switch) {
        switch1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && switch2.isChecked) {
                switch2.isChecked = false
            }
            // Ensure at least one is checked
            if (!isChecked && !switch2.isChecked) {
                switch1.isChecked = true
            }
        }
        
        switch2.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && switch1.isChecked) {
                switch1.isChecked = false
            }
            // Ensure at least one is checked
            if (!isChecked && !switch1.isChecked) {
                switch2.isChecked = true
            }
        }
    }
    
    /**
     * Get current export destination preferences
     */
    fun isLogEmailEnabled(): Boolean = sp.getBoolean(PREF_LOG_EMAIL_ENABLED, true)
    fun isLogCloudEnabled(): Boolean = sp.getBoolean(PREF_LOG_CLOUD_ENABLED, false)
    fun isSettingsLocalEnabled(): Boolean = sp.getBoolean(PREF_SETTINGS_LOCAL_ENABLED, true)
    fun isSettingsCloudEnabled(): Boolean = sp.getBoolean(PREF_SETTINGS_CLOUD_ENABLED, false)
    fun isCsvLocalEnabled(): Boolean = sp.getBoolean(PREF_CSV_LOCAL_ENABLED, true)
    fun isCsvCloudEnabled(): Boolean = sp.getBoolean(PREF_CSV_CLOUD_ENABLED, false)
}
