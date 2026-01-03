package app.aaps.configuration.maintenance.cloud

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageManager
import app.aaps.plugins.configuration.maintenance.cloud.ExportOptionsDialog
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for ExportOptionsDialog
 * 
 * These tests verify the preference reading/writing logic
 * without requiring Android UI components.
 */
class ExportOptionsDialogTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var cloudStorageManager: CloudStorageManager

    private lateinit var sut: ExportOptionsDialog

    @BeforeEach
    fun setup() {
        sut = ExportOptionsDialog(
            aapsLogger = aapsLogger,
            rh = rh,
            sp = sp,
            cloudStorageManager = cloudStorageManager
        )
    }

    // ==================== Default Value Tests ====================

    @Test
    fun `isLogEmailEnabled should return true by default`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_LOG_EMAIL_ENABLED, true)).thenReturn(true)
        
        assertThat(sut.isLogEmailEnabled()).isTrue()
    }

    @Test
    fun `isLogCloudEnabled should return false by default`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_LOG_CLOUD_ENABLED, false)).thenReturn(false)
        
        assertThat(sut.isLogCloudEnabled()).isFalse()
    }

    @Test
    fun `isSettingsLocalEnabled should return true by default`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_SETTINGS_LOCAL_ENABLED, true)).thenReturn(true)
        
        assertThat(sut.isSettingsLocalEnabled()).isTrue()
    }

    @Test
    fun `isSettingsCloudEnabled should return false by default`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_SETTINGS_CLOUD_ENABLED, false)).thenReturn(false)
        
        assertThat(sut.isSettingsCloudEnabled()).isFalse()
    }

    @Test
    fun `isCsvLocalEnabled should return true by default`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_CSV_LOCAL_ENABLED, true)).thenReturn(true)
        
        assertThat(sut.isCsvLocalEnabled()).isTrue()
    }

    @Test
    fun `isCsvCloudEnabled should return false by default`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_CSV_CLOUD_ENABLED, false)).thenReturn(false)
        
        assertThat(sut.isCsvCloudEnabled()).isFalse()
    }

    @Test
    fun `isAllCloudEnabled should return false by default`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_ALL_CLOUD_ENABLED, false)).thenReturn(false)
        
        assertThat(sut.isAllCloudEnabled()).isFalse()
    }

    // ==================== resetToLocalSettings Tests ====================

    @Test
    fun `resetToLocalSettings should disable all cloud options`() {
        sut.resetToLocalSettings()
        
        // Verify All Cloud is disabled
        verify(sp).putBoolean(ExportOptionsDialog.PREF_ALL_CLOUD_ENABLED, false)
        
        // Verify Log is set to Email (not cloud)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_LOG_EMAIL_ENABLED, true)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_LOG_CLOUD_ENABLED, false)
        
        // Verify Settings is set to Local (not cloud)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_SETTINGS_LOCAL_ENABLED, true)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_SETTINGS_CLOUD_ENABLED, false)
        
        // Verify CSV is set to Local (not cloud)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_CSV_LOCAL_ENABLED, true)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_CSV_CLOUD_ENABLED, false)
    }

    // ==================== enableAllCloud Tests ====================

    @Test
    fun `enableAllCloud should enable all cloud options`() {
        sut.enableAllCloud()
        
        // Verify All Cloud is enabled
        verify(sp).putBoolean(ExportOptionsDialog.PREF_ALL_CLOUD_ENABLED, true)
        
        // Verify Log is set to Cloud (not email)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_LOG_EMAIL_ENABLED, false)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_LOG_CLOUD_ENABLED, true)
        
        // Verify Settings is set to Cloud (not local)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_SETTINGS_LOCAL_ENABLED, false)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_SETTINGS_CLOUD_ENABLED, true)
        
        // Verify CSV is set to Cloud (not local)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_CSV_LOCAL_ENABLED, false)
        verify(sp).putBoolean(ExportOptionsDialog.PREF_CSV_CLOUD_ENABLED, true)
    }

    // ==================== Preference Reading Tests ====================

    @Test
    fun `should correctly read enabled log cloud setting`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_LOG_CLOUD_ENABLED, false)).thenReturn(true)
        
        assertThat(sut.isLogCloudEnabled()).isTrue()
    }

    @Test
    fun `should correctly read enabled settings cloud setting`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_SETTINGS_CLOUD_ENABLED, false)).thenReturn(true)
        
        assertThat(sut.isSettingsCloudEnabled()).isTrue()
    }

    @Test
    fun `should correctly read enabled csv cloud setting`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_CSV_CLOUD_ENABLED, false)).thenReturn(true)
        
        assertThat(sut.isCsvCloudEnabled()).isTrue()
    }

    @Test
    fun `should correctly read enabled all cloud setting`() {
        whenever(sp.getBoolean(ExportOptionsDialog.PREF_ALL_CLOUD_ENABLED, false)).thenReturn(true)
        
        assertThat(sut.isAllCloudEnabled()).isTrue()
    }
}
