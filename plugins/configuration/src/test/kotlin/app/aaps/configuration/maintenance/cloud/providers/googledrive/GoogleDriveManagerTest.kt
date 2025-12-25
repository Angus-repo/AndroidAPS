package app.aaps.configuration.maintenance.cloud.providers.googledrive

import android.content.Context
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive.GoogleDriveManager
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for GoogleDriveManager
 * 
 * These tests verify local logic that doesn't require network access.
 * For integration tests that require a real Google Drive connection,
 * see GoogleDriveManagerIntegrationTest.
 */
class GoogleDriveManagerTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context

    private lateinit var sut: GoogleDriveManager

    @BeforeEach
    fun setup() {
        sut = GoogleDriveManager(
            aapsLogger = aapsLogger,
            rh = rh,
            sp = sp,
            rxBus = rxBus,
            context = context
        )
    }

    // ==================== hasValidRefreshToken Tests ====================

    @Test
    fun `hasValidRefreshToken should return false when token is empty`() {
        whenever(sp.getString("google_drive_refresh_token", "")).thenReturn("")
        
        assertThat(sut.hasValidRefreshToken()).isFalse()
    }

    @Test
    fun `hasValidRefreshToken should return true when token exists`() {
        whenever(sp.getString("google_drive_refresh_token", "")).thenReturn("valid_token_here")
        
        assertThat(sut.hasValidRefreshToken()).isTrue()
    }

    @Test
    fun `hasValidRefreshToken should return false when token is blank`() {
        whenever(sp.getString("google_drive_refresh_token", "")).thenReturn("   ")
        
        assertThat(sut.hasValidRefreshToken()).isFalse()
    }

    // ==================== getStorageType Tests ====================

    @Test
    fun `getStorageType should return local by default`() {
        whenever(sp.getString("google_drive_storage_type", "local")).thenReturn("local")
        whenever(sp.getString("google_drive_refresh_token", "")).thenReturn("")
        
        assertThat(sut.getStorageType()).isEqualTo(GoogleDriveManager.STORAGE_TYPE_LOCAL)
    }

    @Test
    fun `getStorageType should return google_drive when set`() {
        whenever(sp.getString("google_drive_storage_type", "local")).thenReturn("google_drive")
        
        assertThat(sut.getStorageType()).isEqualTo(GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE)
    }

    @Test
    fun `getStorageType should restore google_drive when token exists but type is local`() {
        // Scenario: User has a refresh token but storage type was reset to local
        whenever(sp.getString("google_drive_storage_type", "local")).thenReturn("local")
        whenever(sp.getString("google_drive_refresh_token", "")).thenReturn("valid_token")
        whenever(sp.getString("google_drive_folder_id", "")).thenReturn("some_folder_id")
        
        val result = sut.getStorageType()
        
        // Should restore to google_drive and return it
        verify(sp).putString("google_drive_storage_type", "google_drive")
        assertThat(result).isEqualTo(GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE)
    }

    @Test
    fun `getStorageType should not restore when folder id is empty`() {
        whenever(sp.getString("google_drive_storage_type", "local")).thenReturn("local")
        whenever(sp.getString("google_drive_refresh_token", "")).thenReturn("valid_token")
        whenever(sp.getString("google_drive_folder_id", "")).thenReturn("")
        
        val result = sut.getStorageType()
        
        // Should stay local because folder id is not set
        assertThat(result).isEqualTo(GoogleDriveManager.STORAGE_TYPE_LOCAL)
    }

    // ==================== setStorageType Tests ====================

    @Test
    fun `setStorageType should save to SharedPreferences`() {
        sut.setStorageType(GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE)
        
        verify(sp).putString("google_drive_storage_type", "google_drive")
    }

    @Test
    fun `setStorageType should save local type to SharedPreferences`() {
        sut.setStorageType(GoogleDriveManager.STORAGE_TYPE_LOCAL)
        
        verify(sp).putString("google_drive_storage_type", "local")
    }

    // ==================== Storage Type Constants Tests ====================

    @Test
    fun `STORAGE_TYPE_LOCAL should be local`() {
        assertThat(GoogleDriveManager.STORAGE_TYPE_LOCAL).isEqualTo("local")
    }

    @Test
    fun `STORAGE_TYPE_GOOGLE_DRIVE should be google_drive`() {
        assertThat(GoogleDriveManager.STORAGE_TYPE_GOOGLE_DRIVE).isEqualTo("google_drive")
    }

    // ==================== Connection Error State Tests ====================

    @Test
    fun `hasConnectionError should return false initially`() {
        assertThat(sut.hasConnectionError()).isFalse()
    }

    @Test
    fun `clearConnectionError should reset error state`() {
        // First set an error (if there's a method to do so)
        sut.clearConnectionError()
        
        assertThat(sut.hasConnectionError()).isFalse()
    }

    // ==================== clearGoogleDriveSettings Tests ====================

    @Test
    fun `clearGoogleDriveSettings should remove all stored tokens and settings`() {
        sut.clearGoogleDriveSettings()
        
        verify(sp).remove("google_drive_refresh_token")
        verify(sp).remove("google_drive_access_token")
        verify(sp).remove("google_drive_token_expiry")
        verify(sp).remove("google_drive_folder_id")
    }

    // ==================== getSelectedFolderId Tests ====================

    @Test
    fun `getSelectedFolderId should return empty string by default`() {
        whenever(sp.getString("google_drive_folder_id", "")).thenReturn("")
        
        val result = sut.getSelectedFolderId()
        
        assertThat(result).isEmpty()
    }

    @Test
    fun `getSelectedFolderId should return stored folder id`() {
        val expectedFolderId = "folder_123abc"
        whenever(sp.getString("google_drive_folder_id", "")).thenReturn(expectedFolderId)
        
        val result = sut.getSelectedFolderId()
        
        assertThat(result).isEqualTo(expectedFolderId)
    }

    // ==================== setSelectedFolderId Tests ====================

    @Test
    fun `setSelectedFolderId should save folder id to SharedPreferences`() {
        val folderId = "new_folder_id_456"
        
        sut.setSelectedFolderId(folderId)
        
        verify(sp).putString("google_drive_folder_id", folderId)
    }

    @Test
    fun `setSelectedFolderId should handle empty folder id`() {
        sut.setSelectedFolderId("")
        
        verify(sp).putString("google_drive_folder_id", "")
    }

}
