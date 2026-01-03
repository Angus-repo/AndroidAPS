package app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive

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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
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
 * 
 * IMPORTANT: All test files are stored under AAPS/TEST directory to avoid cluttering root.
 * The shared GoogleDriveManager instance in companion object maintains path cache across
 * all tests to prevent duplicate folder creation.
 */
class GoogleDriveManagerIntegrationTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    
    companion object {
        // Use a consistent test root directory under AAPS
        // All test files go to "AAPS/TEST" in Google Drive
        private const val TEST_ROOT_DIR = "TEST"
        
        // Single manager instance to maintain path cache across all tests
        private var sharedManager: GoogleDriveManager? = null
        
    }
    
    // Read refresh token from system property or environment variable
    private val testRefreshTokenRaw = run {
        val sysProp = System.getProperty("GOOGLE_DRIVE_REFRESH_TOKEN")
        val envVar = System.getenv("GOOGLE_DRIVE_REFRESH_TOKEN")
        
        println("\n========================================")
        println("   Google Drive Integration Test Setup")
        println("========================================")
        println("System property = ${if (sysProp == null) "null" else if (sysProp.isEmpty()) "empty" else "set (${sysProp.length} chars)"}")
        println("Environment var = ${if (envVar == null) "null" else if (envVar.isEmpty()) "empty" else "set (${envVar.length} chars)"}")
        
        sysProp ?: envVar
    }
    
    // Parse refresh token (might be Base64 encoded "client_id|token" format)
    private val testRefreshToken = run {
        if (testRefreshTokenRaw.isNullOrEmpty()) {
            println("⚠️  No refresh token provided")
            println("========================================\n")
            return@run null
        }
        
        try {
            // Try Base64 decoding
            val decoded = String(java.util.Base64.getDecoder().decode(testRefreshTokenRaw))
            if (decoded.contains("|")) {
                // Format: client_id|refresh_token
                val parts = decoded.split("|", limit = 2)
                if (parts.size == 2) {
                    println("✓ Detected Base64 encoded format")
                    println("✓ Refresh token extracted (${parts[1].length} chars)")
                    println("========================================\n")
                    return@run parts[1]  // Return only refresh token part
                }
            }
            // Decoding successful but not expected format, use original value
            println("✓ Using decoded token (${decoded.length} chars)")
            println("========================================\n")
            decoded
        } catch (e: Exception) {
            // Base64 decoding failed, use original value directly
            println("✓ Using raw token (${testRefreshTokenRaw.length} chars)")
            println("========================================\n")
            testRefreshTokenRaw
        }
    }
    
    @BeforeEach
    fun setup() {
        org.mockito.kotlin.doAnswer { invocation ->
            val stringId = invocation.getArgument<Int>(0)
            "Resource_$stringId"
        }.whenever(rh).gs(org.mockito.ArgumentMatchers.anyInt())
        
        // Override gs(Int, String) to handle null safely
        org.mockito.kotlin.doAnswer { invocation ->
            val stringId = invocation.getArgument<Int>(0)
            val arg = invocation.getArgument<String?>(1)
            val template = rh.gs(stringId)
            when {
                arg == null -> template
                template.contains("%s") || template.contains("%d") || template.contains("%f") -> 
                    try { String.format(template, arg) } catch (e: Exception) { "$template [$arg]" }
                else -> template
            }
        }.whenever(rh).gs(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString())
        
        // If refresh token exists, set it to SharedPreferences
        if (!testRefreshToken.isNullOrEmpty()) {
            `when`(sp.getString("google_drive_refresh_token", "")).thenReturn(testRefreshToken)
            `when`(sp.getString("google_drive_storage_type", "local")).thenReturn("google_drive")
            `when`(sp.getString("google_drive_access_token", "")).thenReturn("")
            `when`(sp.getLong("google_drive_token_expiry", 0)).thenReturn(0L)
            
            // Mock all SharedPreferences write methods
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
            
            // Create shared manager if not exists
            if (sharedManager == null) {
                sharedManager = GoogleDriveManager(
                    aapsLogger = aapsLogger,
                    rh = rh,
                    sp = sp,
                    rxBus = rxBus,
                    context = context
                )
            }
        }
    }
    
    // Property to access shared manager instance
    private val googleDriveManager: GoogleDriveManager
        get() = sharedManager ?: throw IllegalStateException("GoogleDriveManager not initialized. Refresh token may be missing.")
    
    /**
     * Test if valid access token can be obtained
     */
    @Test
    fun testGetValidAccessToken() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) { 
            println("\n⚠️  SKIPPING: GOOGLE_DRIVE_REFRESH_TOKEN not set\n")
            return@runBlocking 
        }
        
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("  Testing: Get Valid Access Token")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("→ Refresh token: ${testRefreshToken?.take(20)}...${testRefreshToken?.takeLast(10)} (${testRefreshToken?.length} chars)")
        println("→ Attempting to exchange refresh token for access token...")
        
        val accessToken = googleDriveManager.getValidAccessToken()
        
        if (accessToken == null) {
            println("\n❌ FAILED: Unable to obtain access token")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("\n🔍 Possible Causes:")
            println("   1. Refresh token has EXPIRED or been REVOKED")
            println("   2. Network connectivity issues")
            println("   3. Google OAuth API service unavailable")
            println("\n💡 How to Fix:")
            println("   Run the following script to get a new refresh token:")
            println("   → ./get_new_refresh_token.sh")
            println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        }
        
        assertThat(accessToken).isNotNull()
        assertThat(accessToken).isNotEmpty()
        println("\n✅ SUCCESS: Access token obtained")
        println("   → Token: ${accessToken?.take(20)}...${accessToken?.takeLast(10)} (${accessToken?.length} chars)")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    }
    
    /**
     * Test Google Drive connection
     */
    @Test
    fun testConnection() = runBlocking {
        if (testRefreshToken.isNullOrEmpty()) { 
            println("⚠️  Skipping test: GOOGLE_DRIVE_REFRESH_TOKEN environment variable not set")
            return@runBlocking 
        }
        
        val isConnected = googleDriveManager.testConnection()
        
        assertThat(isConnected).isTrue()
        println("✓ Google Drive connection test successful")
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
        
        val testPath = "AAPS/$TEST_ROOT_DIR/export"
        val folderId = googleDriveManager.getOrCreateFolderPath(testPath)
        
        assertThat(folderId).isNotNull()
        assertThat(folderId).isNotEmpty()
        println("✓ Successfully created multi-level folder path: AAPS/$testPath (ID: $folderId)")
    }
    
    /**
     * Test uploading file to settings path under TEST directory
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
        
        // Upload to settings path under test directory (will become AAPS/TEST/export/preferences)
        val testSettingsPath = "AAPS/$TEST_ROOT_DIR/export/preferences"
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = "application/json",
            path = testSettingsPath
        )
        
        assertThat(fileId).isNotNull()
        assertThat(fileId).isNotEmpty()
        println("✓ Successfully uploaded settings file: $fileName")
        println("  Path: AAPS/$testSettingsPath")
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
        
        // Upload to logs path under test directory (will become AAPS/TEST/export/logs)
        val testLogsPath = "AAPS/$TEST_ROOT_DIR/export/logs"
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = "application/zip",
            path = testLogsPath
        )
        
        assertThat(fileId).isNotNull()
        assertThat(fileId).isNotEmpty()
        println("✓ Successfully uploaded log file: $fileName")
        println("  Path: AAPS/$testLogsPath")
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
        
        // Upload to user entries path under test directory (will become AAPS/TEST/export/userentries)
        val testEntriesPath = "AAPS/$TEST_ROOT_DIR/export/userentries"
        val fileId = googleDriveManager.uploadFileToPath(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = "text/csv",
            path = testEntriesPath
        )
        
        assertThat(fileId).isNotNull()
        assertThat(fileId).isNotEmpty()
        println("✓ Successfully uploaded user entries file: $fileName")
        println("  Path: AAPS/$testEntriesPath")
        println("  File ID: $fileId")
    }
    
}
