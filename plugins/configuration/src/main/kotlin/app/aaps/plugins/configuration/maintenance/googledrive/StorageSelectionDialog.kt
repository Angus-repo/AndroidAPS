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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageSelectionDialog @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val googleDriveManager: GoogleDriveManager,
    private val maintenancePlugin: MaintenancePlugin
) {
    
    /**
     * 顯示儲存方式選擇對話框
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
        
        // 設置初始選擇狀態
        updateCardSelection(localCard, localIcon, localText, !isGoogleDriveSelected)
        updateCardSelection(googleDriveCard, googleDriveIcon, googleDriveText, isGoogleDriveSelected)
        
        // 設置描述文字
        localDescription.text = rh.gs(R.string.storage_local_description)
        googleDriveDescription.text = rh.gs(R.string.storage_google_drive_description)
        
        val dialog = AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.select_storage_type))
            .setView(dialogView)
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .create()
        
        localCard.setOnClickListener {
            updateCardSelection(localCard, localIcon, localText, true)
            updateCardSelection(googleDriveCard, googleDriveIcon, googleDriveText, false)
            
            googleDriveManager.setStorageType(GoogleDriveManager.STORAGE_TYPE_LOCAL)
            googleDriveManager.clearConnectionError()
            dialog.dismiss()
            onLocalSelected()
            onStorageChanged()
        }
        
        googleDriveCard.setOnClickListener {
            updateCardSelection(localCard, localIcon, localText, false)
            updateCardSelection(googleDriveCard, googleDriveIcon, googleDriveText, true)
            
            dialog.dismiss()
            handleGoogleDriveSelection(activity, onGoogleDriveSelected, onStorageChanged)
        }
        
        dialog.show()
    }
    
    /**
     * 更新卡片選擇狀態
     */
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
    
    /**
     * 處理 Google Drive 選擇
     */
    private fun handleGoogleDriveSelection(
        activity: DaggerAppCompatActivityWithResult,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        activity.lifecycleScope.launch {
            try {
                if (googleDriveManager.hasValidRefreshToken()) {
                    // 已有 refresh token，測試連線
                    if (googleDriveManager.testConnection()) {
                        googleDriveManager.setStorageType(GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE)
                        onSuccess()
                        onStorageChanged()
                        showGoogleDriveFolderSelection(activity)
                    } else {
                        // 連線失敗，提示重新授權
                        showReauthorizeDialog(activity, onSuccess, onStorageChanged)
                    }
                } else {
                    // 沒有 refresh token，開始 PKCE 授權流程
                    startPKCEAuthFlow(activity, onSuccess, onStorageChanged)
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error handling Google Drive selection", e)
                ToastUtils.errorToast(activity, rh.gs(R.string.google_drive_error, e.message))
            }
        }
    }
    
    /**
     * 開始 PKCE 授權流程
     */
    private suspend fun startPKCEAuthFlow(
        activity: DaggerAppCompatActivityWithResult,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        try {
            val authUrl = googleDriveManager.startPKCEAuth()
            
            // 打開瀏覽器進行授權
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            activity.startActivity(intent)
            
            // 顯示等待對話框並等待授權碼
            showWaitingDialog(activity) { cancelled ->
                if (cancelled) {
                    // 用戶取消了授權
                    ToastUtils.infoToast(activity, "授權已取消")
                } else {
                    // 在背景等待授權碼
                    activity.lifecycleScope.launch {
                        val authCode = googleDriveManager.waitForAuthCode(60000) // 60秒超時
                        
                        if (authCode != null) {
                            if (googleDriveManager.exchangeCodeForTokens(authCode)) {
                                googleDriveManager.setStorageType(GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE)
                                ToastUtils.infoToast(activity, rh.gs(R.string.google_drive_auth_success))
                                onSuccess()
                                onStorageChanged()
                                showGoogleDriveFolderSelection(activity)
                            } else {
                                ToastUtils.errorToast(activity, rh.gs(R.string.google_drive_auth_failed))
                            }
                        } else {
                            ToastUtils.errorToast(activity, "授權超時，請重試")
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
     * 顯示授權指引對話框
     */
    /**
     * 顯示等待授權的對話框
     */
    private fun showWaitingDialog(
        activity: DaggerAppCompatActivityWithResult,
        onResult: (cancelled: Boolean) -> Unit
    ) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.google_drive_authorization))
            .setMessage("已在瀏覽器中開啟 Google Drive 授權頁面。\n\n請在瀏覽器中完成授權，然後返回此應用程式。\n\n授權完成後此對話框會自動關閉。")
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel)) { _, _ ->
                onResult(true)
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // 當授權完成時自動關閉對話框
        onResult(false)
        dialog.dismiss()
    }
    
    /**
     * 顯示重新授權對話框
     */
    private fun showReauthorizeDialog(
        activity: DaggerAppCompatActivityWithResult,
        onSuccess: () -> Unit,
        onStorageChanged: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(rh.gs(R.string.google_drive_connection_failed))
            .setMessage(rh.gs(R.string.google_drive_reauthorize_message))
            .setPositiveButton(rh.gs(R.string.reauthorize)) { _, _ ->
                // 清除舊的授權資料並重新授權
                googleDriveManager.clearGoogleDriveSettings()
                activity.lifecycleScope.launch {
                    startPKCEAuthFlow(activity, onSuccess, onStorageChanged)
                }
            }
            .setNegativeButton(rh.gs(app.aaps.core.ui.R.string.cancel), null)
            .show()
    }
    
    /**
     * 顯示 Google Drive 資料夾選擇對話框
     */
    private fun showGoogleDriveFolderSelection(activity: DaggerAppCompatActivityWithResult) {
        activity.lifecycleScope.launch {
            try {
                val folders = googleDriveManager.listFolders()
                val folderNames = mutableListOf<String>()
                val folderIds = mutableListOf<String>()
                
                // 添加根目錄選項
                folderNames.add(rh.gs(R.string.root_folder))
                folderIds.add("root")
                
                // 添加現有資料夾
                folders.forEach { folder ->
                    folderNames.add(folder.name)
                    folderIds.add(folder.id)
                }
                
                // 添加創建新資料夾選項
                folderNames.add(rh.gs(R.string.create_new_folder))
                folderIds.add("create_new")
                
                AlertDialog.Builder(activity)
                    .setTitle(rh.gs(R.string.select_google_drive_folder))
                    .setItems(folderNames.toTypedArray()) { _, which ->
                        when {
                            which == folderNames.size - 1 -> {
                                // 創建新資料夾
                                showCreateFolderDialog(activity)
                            }
                            else -> {
                                // 選擇現有資料夾
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
    
    /**
     * 顯示創建資料夾對話框
     */
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
    
    /**
     * 創建資料夾
     */
    private fun createFolder(activity: DaggerAppCompatActivityWithResult, folderPath: String) {
        activity.lifecycleScope.launch {
            try {
                // 分割路徑並創建多層資料夾
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
