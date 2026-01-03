package app.aaps.configuration.maintenance.cloud.providers.googledrive

import android.content.Context
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.maintenance.cloud.CloudFile
import app.aaps.plugins.configuration.maintenance.cloud.CloudFolder
import app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive.GoogleDriveProvider
import app.aaps.plugins.configuration.maintenance.cloud.StorageTypes
import app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive.DriveFile
import app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive.DriveFilePage
import app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive.DriveFolder
import app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive.GoogleDriveManager
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for GoogleDriveProvider
 * 
 * These tests verify that GoogleDriveProvider correctly delegates
 * operations to GoogleDriveManager and transforms results appropriately.
 */
class GoogleDriveProviderTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var googleDriveManager: GoogleDriveManager

    private lateinit var sut: GoogleDriveProvider

    @BeforeEach
    fun setup() {
        sut = GoogleDriveProvider(
            aapsLogger = aapsLogger,
            rh = rh,
            sp = sp,
            rxBus = rxBus,
            context = context,
            googleDriveManager = googleDriveManager
        )
    }

    // ==================== Provider Identity Tests ====================

    @Test
    fun `storageType should return GOOGLE_DRIVE`() {
        assertThat(sut.storageType).isEqualTo(StorageTypes.GOOGLE_DRIVE)
    }

    @Test
    fun `displayName should use resource helper`() {
        whenever(rh.gs(any())).thenReturn("Google Drive")
        
        val displayName = sut.displayName
        
        assertThat(displayName).isEqualTo("Google Drive")
    }

    // ==================== Authentication Tests ====================

    @Test
    fun `startAuth should delegate to googleDriveManager`() = runBlocking {
        val expectedAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth?..."
        whenever(googleDriveManager.startPKCEAuth()).thenReturn(expectedAuthUrl)
        
        val result = sut.startAuth()
        
        verify(googleDriveManager).startPKCEAuth()
        assertThat(result).isEqualTo(expectedAuthUrl)
    }

    @Test
    fun `completeAuth should delegate to googleDriveManager`() = runBlocking {
        val authCode = "auth_code_123"
        whenever(googleDriveManager.exchangeCodeForTokens(authCode)).thenReturn(true)
        
        val result = sut.completeAuth(authCode)
        
        verify(googleDriveManager).exchangeCodeForTokens(authCode)
        assertThat(result).isTrue()
    }

    @Test
    fun `completeAuth should return false when exchange fails`() = runBlocking {
        val authCode = "invalid_code"
        whenever(googleDriveManager.exchangeCodeForTokens(authCode)).thenReturn(false)
        
        val result = sut.completeAuth(authCode)
        
        assertThat(result).isFalse()
    }

    @Test
    fun `hasValidCredentials should delegate to googleDriveManager`() {
        whenever(googleDriveManager.hasValidRefreshToken()).thenReturn(true)
        
        val result = sut.hasValidCredentials()
        
        verify(googleDriveManager).hasValidRefreshToken()
        assertThat(result).isTrue()
    }

    @Test
    fun `hasValidCredentials should return false when no token`() {
        whenever(googleDriveManager.hasValidRefreshToken()).thenReturn(false)
        
        val result = sut.hasValidCredentials()
        
        assertThat(result).isFalse()
    }

    @Test
    fun `clearCredentials should delegate to googleDriveManager`() {
        sut.clearCredentials()
        
        verify(googleDriveManager).clearGoogleDriveSettings()
    }

    @Test
    fun `getValidAccessToken should delegate to googleDriveManager`() = runBlocking {
        val expectedToken = "access_token_123"
        whenever(googleDriveManager.getValidAccessToken()).thenReturn(expectedToken)
        
        val result = sut.getValidAccessToken()
        
        verify(googleDriveManager).getValidAccessToken()
        assertThat(result).isEqualTo(expectedToken)
    }

    // ==================== Connection Tests ====================

    @Test
    fun `testConnection should delegate to googleDriveManager`() = runBlocking {
        whenever(googleDriveManager.testConnection()).thenReturn(true)
        
        val result = sut.testConnection()
        
        verify(googleDriveManager).testConnection()
        assertThat(result).isTrue()
    }

    @Test
    fun `testConnection should return false when connection fails`() = runBlocking {
        whenever(googleDriveManager.testConnection()).thenReturn(false)
        
        val result = sut.testConnection()
        
        assertThat(result).isFalse()
    }

    @Test
    fun `hasConnectionError should delegate to googleDriveManager`() {
        whenever(googleDriveManager.hasConnectionError()).thenReturn(true)
        
        val result = sut.hasConnectionError()
        
        verify(googleDriveManager).hasConnectionError()
        assertThat(result).isTrue()
    }

    @Test
    fun `clearConnectionError should delegate to googleDriveManager`() {
        sut.clearConnectionError()
        
        verify(googleDriveManager).clearConnectionError()
    }

    // ==================== Folder Operations Tests ====================

    @Test
    fun `getOrCreateFolderPath should delegate to googleDriveManager`() = runBlocking {
        val path = "AAPS/settings"
        val expectedFolderId = "folder_id_123"
        whenever(googleDriveManager.getOrCreateFolderPath(path)).thenReturn(expectedFolderId)
        
        val result = sut.getOrCreateFolderPath(path)
        
        verify(googleDriveManager).getOrCreateFolderPath(path)
        assertThat(result).isEqualTo(expectedFolderId)
    }

    @Test
    fun `getOrCreateFolderPath should return null when creation fails`() = runBlocking {
        val path = "AAPS/settings"
        whenever(googleDriveManager.getOrCreateFolderPath(path)).thenReturn(null)
        
        val result = sut.getOrCreateFolderPath(path)
        
        assertThat(result).isNull()
    }

    @Test
    fun `createFolder should delegate to googleDriveManager`() = runBlocking {
        val name = "test_folder"
        val parentId = "parent_id"
        val expectedFolderId = "new_folder_id"
        whenever(googleDriveManager.createFolder(name, parentId)).thenReturn(expectedFolderId)
        
        val result = sut.createFolder(name, parentId)
        
        verify(googleDriveManager).createFolder(name, parentId)
        assertThat(result).isEqualTo(expectedFolderId)
    }

    @Test
    fun `listFolders should transform DriveFolder to CloudFolder`() = runBlocking {
        val parentId = "parent_id"
        val driveFolders = listOf(
            DriveFolder("folder1_id", "Folder 1"),
            DriveFolder("folder2_id", "Folder 2")
        )
        whenever(googleDriveManager.listFolders(parentId)).thenReturn(driveFolders)
        
        val result = sut.listFolders(parentId)
        
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo("folder1_id")
        assertThat(result[0].name).isEqualTo("Folder 1")
        assertThat(result[0].parentId).isEqualTo(parentId)
        assertThat(result[1].id).isEqualTo("folder2_id")
        assertThat(result[1].name).isEqualTo("Folder 2")
    }

    @Test
    fun `listFolders should not set parentId when parent is root`() = runBlocking {
        val parentId = "root"
        val driveFolders = listOf(DriveFolder("folder_id", "Folder"))
        whenever(googleDriveManager.listFolders(parentId)).thenReturn(driveFolders)
        
        val result = sut.listFolders(parentId)
        
        assertThat(result[0].parentId).isNull()
    }

    // ==================== File Operations Tests ====================

    @Test
    fun `uploadFileToPath should delegate to googleDriveManager`() = runBlocking {
        val fileName = "test.json"
        val content = "test content".toByteArray()
        val mimeType = "application/json"
        val path = "AAPS/settings"
        val expectedFileId = "file_id_123"
        whenever(googleDriveManager.uploadFileToPath(fileName, content, mimeType, path))
            .thenReturn(expectedFileId)
        
        val result = sut.uploadFileToPath(fileName, content, mimeType, path)
        
        verify(googleDriveManager).uploadFileToPath(fileName, content, mimeType, path)
        assertThat(result).isEqualTo(expectedFileId)
    }

    @Test
    fun `uploadFile should delegate to googleDriveManager`() = runBlocking {
        val fileName = "test.json"
        val content = "test content".toByteArray()
        val mimeType = "application/json"
        val expectedFileId = "file_id_123"
        whenever(googleDriveManager.uploadFile(fileName, content, mimeType))
            .thenReturn(expectedFileId)
        
        val result = sut.uploadFile(fileName, content, mimeType)
        
        verify(googleDriveManager).uploadFile(fileName, content, mimeType)
        assertThat(result).isEqualTo(expectedFileId)
    }

    @Test
    fun `downloadFile should delegate to googleDriveManager`() = runBlocking {
        val fileId = "file_id_123"
        val expectedContent = "file content".toByteArray()
        whenever(googleDriveManager.downloadFile(fileId)).thenReturn(expectedContent)
        
        val result = sut.downloadFile(fileId)
        
        verify(googleDriveManager).downloadFile(fileId)
        assertThat(result).isEqualTo(expectedContent)
    }

    @Test
    fun `downloadFile should return null when download fails`() = runBlocking {
        val fileId = "invalid_file_id"
        whenever(googleDriveManager.downloadFile(fileId)).thenReturn(null)
        
        val result = sut.downloadFile(fileId)
        
        assertThat(result).isNull()
    }

    @Test
    fun `listSettingsFiles should transform DriveFilePage to CloudFileListResult`() = runBlocking {
        val pageSize = 10
        val pageToken: String? = null
        val driveResult = DriveFilePage(
            files = listOf(
                DriveFile("file1_id", "settings_2024.json"),
                DriveFile("file2_id", "settings_2023.json")
            ),
            nextPageToken = "next_token",
            totalCount = 5
        )
        whenever(googleDriveManager.listSettingsFilesPaged(pageToken, pageSize))
            .thenReturn(driveResult)
        
        val result = sut.listSettingsFiles(pageSize, pageToken)
        
        assertThat(result.files).hasSize(2)
        assertThat(result.files[0].id).isEqualTo("file1_id")
        assertThat(result.files[0].name).isEqualTo("settings_2024.json")
        assertThat(result.files[0].mimeType).isEqualTo("application/json")
        assertThat(result.nextPageToken).isEqualTo("next_token")
        assertThat(result.totalCount).isEqualTo(5)
    }

    @Test
    fun `listSettingsFiles should handle pagination token`() = runBlocking {
        val pageSize = 5
        val pageToken = "existing_token"
        val driveResult = DriveFilePage(
            files = listOf(DriveFile("file_id", "test.json")),
            nextPageToken = null,
            totalCount = 1
        )
        whenever(googleDriveManager.listSettingsFilesPaged(pageToken, pageSize))
            .thenReturn(driveResult)
        
        val result = sut.listSettingsFiles(pageSize, pageToken)
        
        verify(googleDriveManager).listSettingsFilesPaged(pageToken, pageSize)
        assertThat(result.nextPageToken).isNull()
    }

    // ==================== Selected Folder Tests ====================

    @Test
    fun `getSelectedFolderId should delegate to googleDriveManager`() {
        val expectedFolderId = "selected_folder_123"
        whenever(googleDriveManager.getSelectedFolderId()).thenReturn(expectedFolderId)
        
        val result = sut.getSelectedFolderId()
        
        verify(googleDriveManager).getSelectedFolderId()
        assertThat(result).isEqualTo(expectedFolderId)
    }

    @Test
    fun `setSelectedFolderId should delegate to googleDriveManager`() {
        val folderId = "folder_id_123"
        
        sut.setSelectedFolderId(folderId)
        
        verify(googleDriveManager).setSelectedFolderId(folderId)
    }

    // ==================== Utility Methods Tests ====================

    @Test
    fun `waitForAuthCode should delegate to googleDriveManager`() = runBlocking {
        val timeout = 30000L
        val expectedCode = "auth_code_123"
        whenever(googleDriveManager.waitForAuthCode(timeout)).thenReturn(expectedCode)
        
        val result = sut.waitForAuthCode(timeout)
        
        verify(googleDriveManager).waitForAuthCode(timeout)
        assertThat(result).isEqualTo(expectedCode)
    }

    @Test
    fun `getCurrentStorageType should delegate to googleDriveManager`() {
        val expectedType = "google_drive"
        whenever(googleDriveManager.getStorageType()).thenReturn(expectedType)
        
        val result = sut.getCurrentStorageType()
        
        verify(googleDriveManager).getStorageType()
        assertThat(result).isEqualTo(expectedType)
    }

    @Test
    fun `setCurrentStorageType should delegate to googleDriveManager`() {
        val storageType = "google_drive"
        
        sut.setCurrentStorageType(storageType)
        
        verify(googleDriveManager).setStorageType(storageType)
    }

    @Test
    fun `countSettingsFiles should delegate to googleDriveManager`() = runBlocking {
        val expectedCount = 15
        whenever(googleDriveManager.countSettingsFiles()).thenReturn(expectedCount)
        
        val result = sut.countSettingsFiles()
        
        verify(googleDriveManager).countSettingsFiles()
        assertThat(result).isEqualTo(expectedCount)
    }

    @Test
    fun `listAllSettingsFiles should transform DriveFile to CloudFile`() = runBlocking {
        val driveFiles = listOf(
            DriveFile("file1_id", "settings.json"),
            DriveFile("file2_id", "backup.csv"),
            DriveFile("file3_id", "data.zip")
        )
        whenever(googleDriveManager.listSettingsFiles()).thenReturn(driveFiles)
        
        val result = sut.listAllSettingsFiles()
        
        assertThat(result).hasSize(3)
        assertThat(result[0].mimeType).isEqualTo("application/json")
        assertThat(result[1].mimeType).isEqualTo("text/csv")
        assertThat(result[2].mimeType).isEqualTo("application/zip")
    }

    // ==================== MIME Type Guessing Tests ====================

    @Test
    fun `listAllSettingsFiles should guess correct mime types`() = runBlocking {
        val driveFiles = listOf(
            DriveFile("id1", "file.JSON"),  // uppercase
            DriveFile("id2", "file.unknown"),
            DriveFile("id3", "file")  // no extension
        )
        whenever(googleDriveManager.listSettingsFiles()).thenReturn(driveFiles)
        
        val result = sut.listAllSettingsFiles()
        
        assertThat(result[0].mimeType).isEqualTo("application/json")
        assertThat(result[1].mimeType).isEqualTo("application/octet-stream")
        assertThat(result[2].mimeType).isEqualTo("application/octet-stream")
    }
}
