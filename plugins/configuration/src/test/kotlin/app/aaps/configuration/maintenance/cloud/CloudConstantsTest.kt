package app.aaps.configuration.maintenance.cloud

import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.cloud.StorageTypes
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for CloudConstants and StorageTypes
 * 
 * These tests verify the constant values used for cloud storage paths
 * and preference keys.
 */
class CloudConstantsTest {

    // ==================== Cloud Path Tests ====================

    @Test
    fun `CLOUD_PATH_SETTINGS should be AAPS export preferences`() {
        assertThat(CloudConstants.CLOUD_PATH_SETTINGS).isEqualTo("AAPS/export/preferences")
    }

    @Test
    fun `CLOUD_PATH_LOGS should be AAPS export logs`() {
        assertThat(CloudConstants.CLOUD_PATH_LOGS).isEqualTo("AAPS/export/logs")
    }

    @Test
    fun `CLOUD_PATH_USER_ENTRIES should be AAPS export user_entries`() {
        assertThat(CloudConstants.CLOUD_PATH_USER_ENTRIES).isEqualTo("AAPS/export/user_entries")
    }

    // ==================== Preference Key Tests ====================

    @Test
    fun `PREF_CLOUD_STORAGE_TYPE should have correct key`() {
        assertThat(CloudConstants.PREF_CLOUD_STORAGE_TYPE).isEqualTo("google_drive_storage_type")
    }

    // ==================== Log Prefix Test ====================

    @Test
    fun `LOG_PREFIX should be Cloud tag`() {
        assertThat(CloudConstants.LOG_PREFIX).isEqualTo("[Cloud]")
    }

    // ==================== Request Code Test ====================

    @Test
    fun `CLOUD_IMPORT_REQUEST_CODE should be 1001`() {
        assertThat(CloudConstants.CLOUD_IMPORT_REQUEST_CODE).isEqualTo(1001)
    }

    // ==================== Page Size Test ====================

    @Test
    fun `DEFAULT_PAGE_SIZE should be 5`() {
        assertThat(CloudConstants.DEFAULT_PAGE_SIZE).isEqualTo(5)
    }

    // ==================== StorageTypes Tests ====================

    @Test
    fun `StorageTypes LOCAL should be local`() {
        assertThat(StorageTypes.LOCAL).isEqualTo("local")
    }

    @Test
    fun `StorageTypes GOOGLE_DRIVE should be google_drive`() {
        assertThat(StorageTypes.GOOGLE_DRIVE).isEqualTo("google_drive")
    }

    @Test
    fun `StorageTypes should contain correct cloud types`() {
        assertThat(StorageTypes.CLOUD_TYPES).containsExactly("google_drive")
    }

    @Test
    fun `StorageTypes should contain all types including local`() {
        assertThat(StorageTypes.ALL_TYPES).containsExactly("local", "google_drive")
    }

    @Test
    fun `isCloudStorage should return true for google_drive`() {
        assertThat(StorageTypes.isCloudStorage("google_drive")).isTrue()
    }

    @Test
    fun `isCloudStorage should return false for local`() {
        assertThat(StorageTypes.isCloudStorage("local")).isFalse()
    }

    @Test
    fun `isValidStorageType should return true for valid types`() {
        assertThat(StorageTypes.isValidStorageType("local")).isTrue()
        assertThat(StorageTypes.isValidStorageType("google_drive")).isTrue()
    }

    @Test
    fun `isValidStorageType should return false for invalid types`() {
        assertThat(StorageTypes.isValidStorageType("invalid")).isFalse()
    }
}
