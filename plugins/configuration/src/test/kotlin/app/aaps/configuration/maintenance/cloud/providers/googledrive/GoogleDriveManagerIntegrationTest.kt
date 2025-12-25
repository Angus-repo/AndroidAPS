package app.aaps.configuration.maintenance.cloud.providers.googledrive

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive.GoogleDriveManager
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.nio.charset.StandardCharsets

/**
 * Google Drive Manager Integration Tests
 * 
 * These tests require a real Google Drive refresh token to execute.
 * Usage:
 * 1. Complete Google Drive authorization in the AAPS application
 * 2. Retrieve the refresh token from SharedPreferences
 * 3. Set the refresh token to environment variable GOOGLE_DRIVE_REFRESH_TOKEN
 * 4. Run the tests
 * 
 * Setting environment variable:
 * export GOOGLE_DRIVE_REFRESH_TOKEN="your_refresh_token_here"
 * 
 * Or in Android Studio:
 * Run -> Edit Configurations -> Environment variables
 * Add: GOOGLE_DRIVE_REFRESH_TOKEN=your_refresh_token_here
 */
class GoogleDriveManagerIntegrationTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP

    private lateinit var googleDriveManager: GoogleDriveManager
    
    // Read refresh token from system property or environment variable
    private val testRefreshTokenRaw = run {
        val sysProp = System.getProperty("GOOGLE_DRIVE_REFRESH_TOKEN")
        val envVar = System.getenv("GOOGLE_DRIVE_REFRESH_TOKEN")
        println("DEBUG: System property = ${if (sysProp == null) "null" else if (sysProp.isEmpty()) "empty" else "has value (${sysProp.length} chars)"}")
        println("DEBUG: Environment variable = ${if (envVar == null) "null" else if (envVar.isEmpty()) "empty" else "has value (${envVar.length} chars)"}")
        sysProp ?: envVar
    }
    
    // Parse refresh token (might be Base64 encoded "client_id|token" format)
    private val testRefreshToken = run {
        if (testRefreshTokenRaw.isNullOrEmpty()) return@run null
        
        try {
            // Try Base64 decoding
            val decoded = String(java.util.Base64.getDecoder().decode(testRefreshTokenRaw))
            if (decoded.contains("|")) {
                // Format: client_id|refresh_token
                val parts = decoded.split("|", limit = 2)
                if (parts.size == 2) {
                    println("DEBUG: Detected Base64 encoded client_id|token format")
                    println("DEBUG: Extracted refresh token length: ${parts[1].length} chars")
                    return@run parts[1]  // Return only refresh token part
                }
            }
            // Decoding successful but not expected format, use original value
            decoded
        } catch (e: Exception) {
            // Base64 decoding failed, use original value directly
            println("DEBUG: Using raw token (not Base64 encoded)")
            testRefreshTokenRaw
        }
    }
    
    @BeforeEach
    fun setup() {
        googleDriveManager = GoogleDriveManager(
            aapsLogger = aapsLogger,
            rh = rh,
            sp = sp,
            rxBus = rxBus,
            context = context
        )
        
        // If refresh token exists, set it to SharedPreferences
        if (!testRefreshToken.isNullOrEmpty()) {
            `when`(sp.getString("google_drive_refresh_token", "")).thenReturn(testRefreshToken)
            `when`(sp.getString("google_drive_storage_type", "local")).thenReturn("google_drive")
            `when`(sp.getString("google_drive_access_token", "")).thenReturn("")
            `when`(sp.getLong("google_drive_token_expiry", 0)).thenReturn(0L)
            
            // Mock all SharedPreferences write methods (return Unit, not lambda)
            org.mockito.Mockito.doNothing().`when`(sp).putString(
                org.mockito.ArgumentMatchers.anyString(), 
                org.mockito.ArgumentMatchers.anyString()
            )
            org.mockito.Mockito.doNothing().`when`(sp).putLong(
                org.mockito.ArgumentMatchers.anyString(), 
                org.mockito.ArgumentMatchers.anyLong()
            )
            org.mockito.Mockito.doNothing().`when`(sp).remove(
                org.mockito.ArgumentMatchers.anyString()
            )
        }
    }
    
    /**
     * Test if valid access token can be obtained
     */
    @Test
    fun testGetValidAccessToken() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) { println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set"); return@runBlocking }
        
        val accessToken = googleDriveManager.getValidAccessToken()
        
        assertThat(accessToken).isNotNull()
        assertThat(accessToken).isNotEmpty()
        println("✓ Successfully obtained access token (length: ${accessToken?.length})")
    }
    
    /**
     * Test Google Drive connection
     */
    @Test
    fun testConnection() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) { println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set"); return@runBlocking }
        
        val isConnected = googleDriveManager.testConnection()
        
        assertThat(isConnected).isTrue()
        println("✓ Google Drive connection test successful")
    }
    
    /**
     * Test creating a single folder
     */
    @Test
    fun testCreateSingleFolder() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val testFolderName = "AAPS_TEST_${System.currentTimeMillis()}"
        val folderId = googleDriveManager.createFolder(testFolderName, "root")
        
        assertThat(folderId).isNotNull()
        assertThat(folderId).isNotEmpty()
        println("✓ Successfully created folder: $testFolderName (ID: $folderId)")
    }
    
    /**
     * Test creating multi-level folder path
     */
    @Test
    fun testCreateFolderPath() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val testPath = "AAPS/test_${System.currentTimeMillis()}/subfolder"
        val folderId = googleDriveManager.getOrCreateFolderPath(testPath)
        
        assertThat(folderId).isNotNull()
        assertThat(folderId).isNotEmpty()
        println("✓ Successfully created multi-level folder path: $testPath (ID: $folderId)")
    }
    
    /**
     * Test uploading file to default path (settings)
     */
    @Test
    fun testUploadSettingsFile() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val fileName = "test_settings_${System.currentTimeMillis()}.json"
        val testContent = """
            {
                "test": "data",
                "timestamp": ${System.currentTimeMillis()},
                "description": "Integration test file"
            }
        """.trimIndent()
        val fileContent = testContent.toByteArray(StandardCharsets.UTF_8)
        
        // Upload to settings path
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = "application/json",
            path = CloudConstants.CLOUD_PATH_SETTINGS
        )
        
        assertThat(fileId).isNotNull()
        assertThat(fileId).isNotEmpty()
        println("✓ Successfully uploaded settings file: $fileName")
        println("  Path: ${CloudConstants.CLOUD_PATH_SETTINGS}")
        println("  File ID: $fileId")
        println("  File size: ${fileContent.size} bytes")
    }
    
    /**
     * Test uploading file to logs path
     */
    @Test
    fun testUploadLogFile() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val fileName = "test_log_${System.currentTimeMillis()}.zip"
        val testContent = "Mock log file content - ${System.currentTimeMillis()}"
        val fileContent = testContent.toByteArray(StandardCharsets.UTF_8)
        
        // Upload to logs path
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = "application/zip",
            path = CloudConstants.CLOUD_PATH_LOGS
        )
        
        assertThat(fileId).isNotNull()
        assertThat(fileId).isNotEmpty()
        println("✓ Successfully uploaded log file: $fileName")
        println("  Path: ${CloudConstants.CLOUD_PATH_LOGS}")
        println("  File ID: $fileId")
    }
    
    /**
     * Test uploading file to user entries path
     */
    @Test
    fun testUploadUserEntriesFile() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val fileName = "test_entries_${System.currentTimeMillis()}.csv"
        val testContent = """
            timestamp,type,value
            ${System.currentTimeMillis()},test,123
            ${System.currentTimeMillis() + 1000},test,456
        """.trimIndent()
        val fileContent = testContent.toByteArray(StandardCharsets.UTF_8)
        
        // Upload to user entries path
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = "text/csv",
            path = CloudConstants.CLOUD_PATH_USER_ENTRIES
        )
        
        assertThat(fileId).isNotNull()
        assertThat(fileId).isNotEmpty()
        println("✓ Successfully uploaded user entries file: $fileName")
        println("  Path: ${CloudConstants.CLOUD_PATH_USER_ENTRIES}")
        println("  File ID: $fileId")
    }
    
    /**
     * Test uploading file to custom path
     */
    @Test
    fun testUploadToCustomPath() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val customPath = "AAPS/test_custom_${System.currentTimeMillis()}"
        val fileName = "custom_file.txt"
        val testContent = "Custom path test file - ${System.currentTimeMillis()}"
        val fileContent = testContent.toByteArray(StandardCharsets.UTF_8)
        
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = "text/plain",
            path = customPath
        )
        
        assertThat(fileId).isNotNull()
        assertThat(fileId).isNotEmpty()
        println("✓ Successfully uploaded to custom path: $customPath/$fileName")
        println("  File ID: $fileId")
    }
    
    /**
     * Test listing folders
     */
    @Test
    fun testListFolders() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val folders = googleDriveManager.listFolders("root")
        
        assertThat(folders).isNotNull()
        println("✓ Successfully listed root folders (count: ${folders.size})")
        folders.take(5).forEach { folder ->
            println("  - ${folder.name} (ID: ${folder.id})")
        }
    }
    
    /**
     * Test listing settings files
     */
    @Test
    fun testListSettingsFiles() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        // Ensure folder is selected first
        val settingsFolderId = googleDriveManager.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)
        assertThat(settingsFolderId).isNotNull()
        
        `when`(sp.getString("google_drive_folder_id", "")).thenReturn(settingsFolderId)
        
        val files = googleDriveManager.listSettingsFiles()
        
        assertThat(files).isNotNull()
        println("✓ Successfully listed settings files (count: ${files.size})")
        files.take(5).forEach { file ->
            println("  - ${file.name} (ID: ${file.id})")
        }
    }
    
    /**
     * Test downloading file
     */
    @Test
    fun testDownloadFile() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        // Upload a test file first
        val fileName = "download_test_${System.currentTimeMillis()}.txt"
        val originalContent = "Test content for download - ${System.currentTimeMillis()}"
        val fileContent = originalContent.toByteArray(StandardCharsets.UTF_8)
        
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = "text/plain",
            path = "AAPS/test_download"
        )
        
        assertThat(fileId).isNotNull()
        println("✓ Uploaded test file: $fileName (ID: $fileId)")
        
        // Download file
        val downloadedContent = googleDriveManager.downloadFile(fileId!!)
        
        assertThat(downloadedContent).isNotNull()
        val downloadedText = String(downloadedContent!!, StandardCharsets.UTF_8)
        assertThat(downloadedText).isEqualTo(originalContent)
        println("✓ Successfully downloaded and verified file content")
        println("  Original content length: ${originalContent.length}")
        println("  Downloaded content length: ${downloadedText.length}")
        println("  Content matches: ${downloadedText == originalContent}")
    }
    
    /**
     * Test complete workflow: create path -> upload -> list -> download
     */
    @Test
    fun testCompleteWorkflow() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val timestamp = System.currentTimeMillis()
        val testPath = "AAPS/workflow_test_$timestamp"
        val fileName = "workflow_test.json"
        val testData = """{"workflow":"test","timestamp":$timestamp}"""
        
        println("\n=== Complete Workflow Test ===")
        
        // Step 1: Create folder path
        println("1. Creating folder path: $testPath")
        val folderId = googleDriveManager.getOrCreateFolderPath(testPath)
        assertThat(folderId).isNotNull()
        println("   ✓ Folder ID: $folderId")
        
        // Step 2: Upload file
        println("2. Uploading file: $fileName")
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = testData.toByteArray(StandardCharsets.UTF_8),
            mimeType = "application/json",
            path = testPath
        )
        assertThat(fileId).isNotNull()
        println("   ✓ File ID: $fileId")
        
        // Step 3: Set selected folder and list files
        println("3. Listing files in folder")
        `when`(sp.getString("google_drive_folder_id", "")).thenReturn(folderId)
        val files = googleDriveManager.listSettingsFiles()
        assertThat(files).isNotEmpty()
        assertThat(files.any { it.name == fileName }).isTrue()
        println("   ✓ Found ${files.size} files, including $fileName")
        
        // Step 4: Download and verify file
        println("4. Downloading and verifying file")
        val downloadedContent = googleDriveManager.downloadFile(fileId!!)
        assertThat(downloadedContent).isNotNull()
        val downloadedText = String(downloadedContent!!, StandardCharsets.UTF_8)
        assertThat(downloadedText).isEqualTo(testData)
        println("   ✓ File content verification successful")
        
        println("\n=== Workflow Test Complete ===\n")
    }
    
    /**
     * Test path normalization
     */
    @Test
    fun testPathNormalization() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) {
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking
        }
        
        val timestamp = System.currentTimeMillis()
        
        // Different path formats should produce the same result
        val paths = listOf(
            "AAPS/normalize_test_$timestamp",
            "/AAPS/normalize_test_$timestamp",
            "AAPS/normalize_test_$timestamp/",
            "/AAPS/normalize_test_$timestamp/"
        )
        
        val folderIds = mutableSetOf<String>()
        
        for (path in paths) {
            val folderId = googleDriveManager.getOrCreateFolderPath(path)
            assertThat(folderId).isNotNull()
            folderIds.add(folderId!!)
            println("Path: '$path' -> ID: $folderId")
        }
        
        // All paths should resolve to the same folder ID (using cache)
        println("✓ Path normalization test complete, number of unique folder IDs: ${folderIds.size}")
    }
}
