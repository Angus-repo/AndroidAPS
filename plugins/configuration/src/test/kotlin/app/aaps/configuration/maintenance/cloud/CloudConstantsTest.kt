package app.aaps.configuration.maintenance.cloud

import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for CloudConstants
 * 
 * These tests verify the constant values used for cloud storage paths
 * and preference keys.
 */
class CloudConstantsTest {

    // ==================== Cloud Path Tests ====================

    @Test
    fun `CLOUD_PATH_SETTINGS should be AAPS settings`() {
        assertThat(CloudConstants.CLOUD_PATH_SETTINGS).isEqualTo("AAPS/settings")
    }

    @Test
    fun `CLOUD_PATH_LOGS should be AAPS logs`() {
        assertThat(CloudConstants.CLOUD_PATH_LOGS).isEqualTo("AAPS/logs")
    }

    @Test
    fun `CLOUD_PATH_CSV should be AAPS csv`() {
        assertThat(CloudConstants.CLOUD_PATH_CSV).isEqualTo("AAPS/csv")
    }

    // ==================== Preference Key Tests ====================

    @Test
    fun `PREF_CLOUD_FOLDER_ID_SETTINGS should have correct key`() {
        assertThat(CloudConstants.PREF_CLOUD_FOLDER_ID_SETTINGS)
            .isEqualTo("cloud_folder_id_settings")
    }

    @Test
    fun `PREF_CLOUD_FOLDER_ID_LOGS should have correct key`() {
        assertThat(CloudConstants.PREF_CLOUD_FOLDER_ID_LOGS)
            .isEqualTo("cloud_folder_id_logs")
    }

    @Test
    fun `PREF_CLOUD_FOLDER_ID_CSV should have correct key`() {
        assertThat(CloudConstants.PREF_CLOUD_FOLDER_ID_CSV)
            .isEqualTo("cloud_folder_id_csv")
    }

    // ==================== Log Prefix Test ====================

    @Test
    fun `LOG_PREFIX should be Cloud tag`() {
        assertThat(CloudConstants.LOG_PREFIX).isEqualTo("[Cloud]")
    }

    // ==================== Storage Type Preference Key Test ====================

    @Test
    fun `PREF_STORAGE_TYPE should have correct key`() {
        assertThat(CloudConstants.PREF_STORAGE_TYPE).isEqualTo("cloud_storage_type")
    }
}
