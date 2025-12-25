package app.aaps.configuration.maintenance.cloud

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageManager
import app.aaps.plugins.configuration.maintenance.cloud.ExportOptionsDialog
import app.aaps.plugins.configuration.maintenance.cloud.CloudDirectoryDialog
import app.aaps.plugins.configuration.maintenance.cloud.StorageType
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Unit tests for CloudDirectoryDialog
 * 
 * These tests verify the cloud storage selection logic
 * without requiring Android UI components.
 */
class CloudDirectoryDialogTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var cloudStorageManager: CloudStorageManager
    @Mock lateinit var exportOptionsDialog: ExportOptionsDialog

    private lateinit var sut: CloudDirectoryDialog

    @BeforeEach
    fun setup() {
        sut = CloudDirectoryDialog(
            aapsLogger = aapsLogger,
            rh = rh,
            sp = sp,
            cloudStorageManager = cloudStorageManager,
            exportOptionsDialog = exportOptionsDialog
        )
    }

    // ==================== Storage Type Tests ====================

    @Test
    fun `getCurrentStorageType should return LOCAL by default`() {
        whenever(cloudStorageManager.getCurrentStorageType()).thenReturn(StorageType.LOCAL)
        
        assertThat(sut.getCurrentStorageType()).isEqualTo(StorageType.LOCAL)
    }

    @Test
    fun `getCurrentStorageType should return GOOGLE_DRIVE when set`() {
        whenever(cloudStorageManager.getCurrentStorageType()).thenReturn(StorageType.GOOGLE_DRIVE)
        
        assertThat(sut.getCurrentStorageType()).isEqualTo(StorageType.GOOGLE_DRIVE)
    }

    @Test
    fun `isCloudStorageEnabled should return true when storage type is GOOGLE_DRIVE`() {
        whenever(cloudStorageManager.getCurrentStorageType()).thenReturn(StorageType.GOOGLE_DRIVE)
        
        assertThat(sut.isCloudStorageEnabled()).isTrue()
    }

    @Test
    fun `isCloudStorageEnabled should return false when storage type is LOCAL`() {
        whenever(cloudStorageManager.getCurrentStorageType()).thenReturn(StorageType.LOCAL)
        
        assertThat(sut.isCloudStorageEnabled()).isFalse()
    }

    // ==================== Folder ID Tests ====================

    @Test
    fun `getSelectedFolderId should return stored folder ID for settings`() {
        val expectedFolderId = "test-folder-id-123"
        whenever(sp.getString(CloudConstants.PREF_CLOUD_FOLDER_ID_SETTINGS, ""))
            .thenReturn(expectedFolderId)
        
        val result = sut.getSelectedFolderId(CloudConstants.CLOUD_PATH_SETTINGS)
        
        assertThat(result).isEqualTo(expectedFolderId)
    }

    @Test
    fun `getSelectedFolderId should return stored folder ID for logs`() {
        val expectedFolderId = "log-folder-id-456"
        whenever(sp.getString(CloudConstants.PREF_CLOUD_FOLDER_ID_LOGS, ""))
            .thenReturn(expectedFolderId)
        
        val result = sut.getSelectedFolderId(CloudConstants.CLOUD_PATH_LOGS)
        
        assertThat(result).isEqualTo(expectedFolderId)
    }

    @Test
    fun `getSelectedFolderId should return stored folder ID for csv`() {
        val expectedFolderId = "csv-folder-id-789"
        whenever(sp.getString(CloudConstants.PREF_CLOUD_FOLDER_ID_CSV, ""))
            .thenReturn(expectedFolderId)
        
        val result = sut.getSelectedFolderId(CloudConstants.CLOUD_PATH_CSV)
        
        assertThat(result).isEqualTo(expectedFolderId)
    }

    @Test
    fun `getSelectedFolderId should return empty string for unknown path`() {
        val result = sut.getSelectedFolderId("unknown/path")
        
        assertThat(result).isEmpty()
    }

    @Test
    fun `setSelectedFolderId should save folder ID for settings`() {
        val folderId = "new-settings-folder-id"
        
        sut.setSelectedFolderId(CloudConstants.CLOUD_PATH_SETTINGS, folderId)
        
        verify(sp).putString(CloudConstants.PREF_CLOUD_FOLDER_ID_SETTINGS, folderId)
    }

    @Test
    fun `setSelectedFolderId should save folder ID for logs`() {
        val folderId = "new-logs-folder-id"
        
        sut.setSelectedFolderId(CloudConstants.CLOUD_PATH_LOGS, folderId)
        
        verify(sp).putString(CloudConstants.PREF_CLOUD_FOLDER_ID_LOGS, folderId)
    }

    @Test
    fun `setSelectedFolderId should save folder ID for csv`() {
        val folderId = "new-csv-folder-id"
        
        sut.setSelectedFolderId(CloudConstants.CLOUD_PATH_CSV, folderId)
        
        verify(sp).putString(CloudConstants.PREF_CLOUD_FOLDER_ID_CSV, folderId)
    }

    @Test
    fun `setSelectedFolderId should not save for unknown path`() {
        sut.setSelectedFolderId("unknown/path", "some-folder-id")
        
        verify(sp, never()).putString(any(), any())
    }

    // ==================== Local Storage Selection Tests ====================

    @Test
    fun `onLocalSelected should set storage type to LOCAL`() {
        sut.onLocalSelected()
        
        verify(cloudStorageManager).setStorageType(StorageType.LOCAL)
    }

    @Test
    fun `onLocalSelected should reset export destination to local settings`() {
        sut.onLocalSelected()
        
        verify(exportOptionsDialog).resetToLocalSettings()
    }

    // ==================== Cloud Storage Selection Tests ====================

    @Test
    fun `onCloudSelected with GOOGLE_DRIVE should set storage type`() {
        sut.onCloudSelected(StorageType.GOOGLE_DRIVE)
        
        verify(cloudStorageManager).setStorageType(StorageType.GOOGLE_DRIVE)
    }

    @Test
    fun `onCloudSelected should enable all cloud options in export destination`() {
        sut.onCloudSelected(StorageType.GOOGLE_DRIVE)
        
        verify(exportOptionsDialog).enableAllCloud()
    }

    // ==================== Folder Path Display Tests ====================

    @Test
    fun `getDisplayFolderPath for settings should return CLOUD_PATH_SETTINGS`() {
        val result = sut.getDisplayFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)
        
        assertThat(result).isEqualTo(CloudConstants.CLOUD_PATH_SETTINGS)
    }

    @Test
    fun `getDisplayFolderPath for logs should return CLOUD_PATH_LOGS`() {
        val result = sut.getDisplayFolderPath(CloudConstants.CLOUD_PATH_LOGS)
        
        assertThat(result).isEqualTo(CloudConstants.CLOUD_PATH_LOGS)
    }

    @Test
    fun `getDisplayFolderPath for csv should return CLOUD_PATH_CSV`() {
        val result = sut.getDisplayFolderPath(CloudConstants.CLOUD_PATH_CSV)
        
        assertThat(result).isEqualTo(CloudConstants.CLOUD_PATH_CSV)
    }
}
