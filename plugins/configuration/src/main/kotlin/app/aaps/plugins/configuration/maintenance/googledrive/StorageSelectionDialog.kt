package app.aaps.plugins.configuration.maintenance.googledrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.core.interfaces.plugin.ActivePlugin
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageSelectionDialog @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val googleDriveManager: GoogleDriveManager,
    private val maintenancePlugin: MaintenancePlugin,
    private val activePlugin: ActivePlugin
) {
    
    /**
     * Show storage selection dialog
     */
    fun showStorageSelectionDialog(
        activity: DaggerAppCompatActivityWithResult,
        onLocalSelected: () -> Unit = { maintenancePlugin.selectAapsDirectory(activity) },
        onGoogleDriveSelected: () -> Unit = {},
        onStorageChanged: () -> Unit = {}
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_storage_selection, null)
        
        val localCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.local_storage_card)
        val googleDriveCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.google_drive_card)
        val localIcon = dialogView.findViewById<ImageView>(R.id.local_storage_icon)
        val googleDriveIcon = dialogView.findViewById<ImageView>(R.id.google_drive_icon)
        val localText = dialogView.findViewById<TextView>(R.id.local_storage_text)
        val googleDriveText = dialogView.findViewById<TextView>(R.id.google_drive_text)
        val localDescription = dialogView.findViewById<TextView>(R.id.local_storage_description)
        val googleDriveDescription = dialogView.findViewById<TextView>(R.id.google_drive_description)
        
        val currentType = googleDriveManager.getStorageType()
        val isGoogleDriveSelected = currentType == GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE
        
        // Initial selection state
        updateCardSelection(localCard, localIcon, localText, !isGoogleDriveSelected)
        updateCardSelection(googleDriveCard, googleDriveIcon, googleDriveText, isGoogleDriveSelected)
        
        // Descriptions (English by default)
        localDescription.text = rh.gs(R.string.storage_local_description)
        googleDriveDescription.text = rh.gs(R.string.storage_google_drive_description)
        
        val dialog = AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.select_storage_type))
            .setView(dialogView)
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .create()
        
        localCard.setOnClickListener {
            // When switching from non-local to local, ask Yes/No and keep this dialog open
            val wasGoogleDrive = googleDriveManager.getStorageType() == GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE
            if (wasGoogleDrive) {
                AlertDialog.Builder(activity)
                    .setTitle("Switch to Local")
                    .setMessage("Switch to local storage. Clear cloud authorization?")
                    .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.yes)) { _, _ ->
                        // Yes: clear cloud tokens and switch to local, keep dialog open
                        googleDriveManager.clearGoogleDriveSettings()
                        googleDriveManager.setStorageType(GoogleDriveManager.STORAGE_TYPE_LOCAL)
                        googleDriveManager.clearConnectionError()
                        updateCardSelection(localCard, localIcon, localText, true)
                        updateCardSelection(googleDriveCard, googleDriveIcon, googleDriveText, false)
                        onLocalSelected()
                        onStorageChanged()
                    }
                    .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.no)) { _, _ ->
                        // No: keep cloud info, switch to local, keep dialog open
                        googleDriveManager.setStorageType(GoogleDriveManager.STORAGE_TYPE_LOCAL)
                        googleDriveManager.clearConnectionError()
                        updateCardSelection(localCard, localIcon, localText, true)
                        updateCardSelection(googleDriveCard, googleDriveIcon, googleDriveText, false)
                        onLocalSelected()
                        onStorageChanged()
                    }
                    .show()
            } else {
                // Already local: ensure state and keep dialog open
                googleDriveManager.setStorageType(GoogleDriveManager.STORAGE_TYPE_LOCAL)
                googleDriveManager.clearConnectionError()
                updateCardSelection(localCard, localIcon, localText, true)
                updateCardSelection(googleDriveCard, googleDriveIcon, googleDriveText, false)
                onLocalSelected()
                onStorageChanged()
            }
        }
        
        googleDriveCard.setOnClickListener {
            updateCardSelection(localCard, localIcon, localText, false)
            updateCardSelection(googleDriveCard, googleDriveIcon, googleDriveText, true)
            
            dialog.dismiss()
            handleGoogleDriveSelection(activity, onGoogleDriveSelected, onStorageChanged)
        }
        
        dialog.show()
    }
    
    /** Update card selection visuals */
    private fun updateCardSelection(card: com.google.android.material.card.MaterialCardView, icon: ImageView, text: TextView, isSelected: Boolean) {
        val context = card.context
        if (isSelected) {
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.storage_card_selected))
            card.strokeColor = ContextCompat.getColor(context, R.color.storage_card_selected_stroke)
            card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.storage_card_selected_stroke_width)
            icon.setColorFilter(ContextCompat.getColor(context, R.color.storage_icon_selected))
            text.setTextColor(ContextCompat.getColor(context, R.color.storage_text_selected))
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.storage_card_normal))
            card.strokeColor = ContextCompat.getColor(context, R.color.storage_card_normal_stroke)
            card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.storage_card_normal_stroke_width)
            icon.setColorFilter(ContextCompat.getColor(context, R.color.storage_icon_normal))
            text.setTextColor(ContextCompat.getColor(context, R.color.storage_text_normal))
        }
    }
    
    /** Handle Google Drive selection */
    private fun handleGoogleDriveSelection(
        activity: DaggerAppCompatActivityWithResult,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        activity.lifecycleScope.launch {
            try {
                if (googleDriveManager.hasValidRefreshToken()) {
                    if (googleDriveManager.testConnection()) {
                        googleDriveManager.setStorageType(GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE)
                        onSuccess()
                        onStorageChanged()
                        showGoogleDriveFolderSelection(activity)
                    } else {
                        showReauthorizeDialog(activity, onSuccess, onStorageChanged)
                    }
                } else {
                    startPKCEAuthFlow(activity, onSuccess, onStorageChanged)
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error handling Google Drive selection", e)
                ToastUtils.errorToast(activity, rh.gs(R.string.google_drive_error, e.message))
            }
        }
    }
    
    /** Start PKCE auth flow */
    private suspend fun startPKCEAuthFlow(
        activity: DaggerAppCompatActivityWithResult,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        try {
            val authUrl = googleDriveManager.startPKCEAuth()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            activity.startActivity(intent)
            showWaitingDialog(activity) { cancelled ->
                if (cancelled) {
                    ToastUtils.infoToast(activity, "Authorization cancelled")
                } else {
                    activity.lifecycleScope.launch {
                        val authCode = googleDriveManager.waitForAuthCode(60000)
                        if (authCode != null) {
                            if (googleDriveManager.exchangeCodeForTokens(authCode)) {
                                googleDriveManager.setStorageType(GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE)
                                ToastUtils.infoToast(activity, rh.gs(R.string.google_drive_auth_success))
                                // 明確打開維護頁，避免被主頁面搶回
                                openMaintenanceScreen(activity)
                                onSuccess()
                                onStorageChanged()
                                activity.lifecycleScope.launch {
                                    kotlinx.coroutines.delay(400)
                                    try {
                                        showGoogleDriveFolderSelection(activity)
                                    } catch (_: Exception) { }
                                }
                            } else {
                                ToastUtils.errorToast(activity, rh.gs(R.string.google_drive_auth_failed))
                            }
                        } else {
                            ToastUtils.errorToast(activity, "Authorization timed out, please retry")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error starting PKCE auth flow", e)
            ToastUtils.errorToast(activity, rh.gs(R.string.google_drive_auth_error, e.message))
        }
    }

    /**
     * 明確開啟MaintenanceFragment所在的SingleFragmentActivity，並延遲再次強化一次，
     * 避免瀏覽器關閉/系統回到上一App時把焦點帶回MainActivity。
     */
    private fun openMaintenanceScreen(activity: DaggerAppCompatActivityWithResult) {
        try {
            val list = activePlugin.getPluginsList()
            val idx = list.indexOfFirst { it is MaintenancePlugin }
            if (idx >= 0) {
                val intent = Intent(activity, SingleFragmentActivity::class.java)
                    .setAction("StorageSelectionDialog")
                    .putExtra("plugin", idx)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                activity.startActivity(intent)
                // 再延遲一次，抵消可能晚來的系統回前景動作
                activity.window?.decorView?.postDelayed({
                    try { activity.startActivity(intent) } catch (_: Exception) { }
                }, 1200)
            } else {
                // 後備：若找不到索引，仍嘗試把當前Activity帶到前景
                bringAppToForeground(activity)
            }
        } catch (_: Exception) { }
    }

    // 後備方法：將應用帶回前景（使用啟動 Intent，並以兩次 REORDER_TO_FRONT 穩定焦點）
    private fun bringAppToForeground(activity: DaggerAppCompatActivityWithResult) {
        try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            if (launchIntent != null) {
                activity.startActivity(launchIntent)
                activity.window?.decorView?.postDelayed({
                    try { activity.startActivity(launchIntent) } catch (_: Exception) { }
                }, 800)
            }
        } catch (_: Exception) { }
    }
    
    /** Waiting dialog for authorization */
    private fun showWaitingDialog(
        activity: DaggerAppCompatActivityWithResult,
        onResult: (cancelled: Boolean) -> Unit
    ) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.google_drive_authorization))
            .setMessage("Google Drive authorization page has been opened in your browser.\n\nPlease complete the authorization, then return to this app.\n\nThis dialog will close automatically once authorization is completed.")
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel)) { _, _ ->
                onResult(true)
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Immediately signal waiting started (dialog stays until flow finishes externally)
        onResult(false)
        dialog.dismiss()
    }
    
    /** Reauthorize dialog */
    private fun showReauthorizeDialog(
        activity: DaggerAppCompatActivityWithResult,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.google_drive_connection_failed))
            .setMessage(rh.gs(R.string.google_drive_reauthorize_message))
            .setPositiveButton(rh.gs(R.string.reauthorize)) { _, _ ->
                googleDriveManager.clearGoogleDriveSettings()
                activity.lifecycleScope.launch {
                    startPKCEAuthFlow(activity, onSuccess, onStorageChanged)
                }
            }
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .show()
    }
    
    /** Show Google Drive folder selection */
    private fun showGoogleDriveFolderSelection(activity: DaggerAppCompatActivityWithResult) {
        activity.lifecycleScope.launch {
            try {
                val folders = googleDriveManager.listFolders()
                val folderNames = mutableListOf<String>()
                val folderIds = mutableListOf<String>()
                
                folderNames.add(rh.gs(R.string.root_folder))
                folderIds.add("root")
                
                folders.forEach { folder ->
                    folderNames.add(folder.name)
                    folderIds.add(folder.id)
                }
                
                folderNames.add(rh.gs(R.string.create_new_folder))
                folderIds.add("create_new")
                
                AlertDialog.Builder(activity)
                    .setTitle(rh.gs(R.string.select_google_drive_folder))
                    .setItems(folderNames.toTypedArray()) { _, which ->
                        when {
                            which == folderNames.size - 1 -> {
                                showCreateFolderDialog(activity)
                            }
                            else -> {
                                val selectedFolderId = folderIds[which]
                                googleDriveManager.setSelectedFolderId(selectedFolderId)
                                
                                if (selectedFolderId == "root") {
                                    ToastUtils.infoToast(activity, rh.gs(R.string.selected_root_folder))
                                } else {
                                    val folderName = folderNames[which]
                                    ToastUtils.infoToast(activity, rh.gs(R.string.selected_folder, folderName))
                                }
                            }
                        }
                    }
                    .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
                    .show()
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error showing folder selection", e)
                ToastUtils.errorToast(activity, rh.gs(R.string.google_drive_folder_error, e.message))
            }
        }
    }
    
    /** Show create folder dialog */
    private fun showCreateFolderDialog(activity: DaggerAppCompatActivityWithResult) {
        val editText = android.widget.EditText(activity)
        editText.hint = rh.gs(R.string.folder_name)
        editText.setText("AAPS/settings")
        
        AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.create_folder))
            .setView(editText)
            .setPositiveButton(rh.gs(R.string.create)) { _, _ ->
                val folderName = editText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    createFolder(activity, folderName)
                } else {
                    ToastUtils.errorToast(activity, rh.gs(R.string.folder_name_required))
                }
            }
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .show()
    }
    
    /** Create folder (support nested path) */
    private fun createFolder(activity: DaggerAppCompatActivityWithResult, folderPath: String) {
        activity.lifecycleScope.launch {
            try {
                val pathParts = folderPath.split("/").filter { it.isNotEmpty() }
                var currentParentId = "root"
                
                for (folderName in pathParts) {
                    val folderId = googleDriveManager.createFolder(folderName, currentParentId)
                    if (folderId != null) {
                        currentParentId = folderId
                    } else {
                        ToastUtils.errorToast(activity, rh.gs(R.string.folder_creation_failed, folderName))
                        return@launch
                    }
                }
                
                googleDriveManager.setSelectedFolderId(currentParentId)
                ToastUtils.infoToast(activity, rh.gs(R.string.folder_created_successfully, folderPath))
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error creating folder", e)
                ToastUtils.errorToast(activity, rh.gs(R.string.folder_creation_error, e.message))
            }
        }
    }
}
