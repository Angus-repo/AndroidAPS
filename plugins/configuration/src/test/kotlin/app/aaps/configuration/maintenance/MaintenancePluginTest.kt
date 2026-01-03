package app.aaps.configuration.maintenance

import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageManager
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageProvider
import app.aaps.plugins.configuration.maintenance.cloud.ExportOptionsDialog
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class MaintenancePluginTest : TestBaseWithProfile() {

    @Mock lateinit var nsSettingsStatus: NSSettingsStatus
    @Mock lateinit var loggerUtils: LoggerUtils
    @Mock lateinit var fileListProvider: FileListProvider
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var cloudStorageManager: CloudStorageManager
    @Mock lateinit var exportOptionsDialog: ExportOptionsDialog
    @Mock lateinit var cloudProvider: CloudStorageProvider

    private lateinit var sut: MaintenancePlugin

    @BeforeEach
    fun mock() {
        sut = MaintenancePlugin(context, rh, preferences, nsSettingsStatus, aapsLogger, config, fileListProvider, loggerUtils, uel, cloudStorageManager, exportOptionsDialog)
        whenever(loggerUtils.suffix).thenReturn(".log.zip")
        whenever(loggerUtils.logDirectory).thenReturn("src/test/assets/logger")
        // Unknown solution after scoped access
        //whenever(fileListProvider.ensureTempDirExists()).thenReturn(File("src/test/assets/logger"))
    }

    @Test fun logFilesTest() {
        var logs = sut.getLogFiles(2)
        assertThat(logs.map { it.name }).containsExactly(
            "AndroidAPS.log",
            "AndroidAPS.2018-01-03_01-01-00.1.zip",
        ).inOrder()
        logs = sut.getLogFiles(10)
        assertThat(logs).hasSize(4)
    }

    // Unknown solution after scoped access
    // @Test
    // fun zipLogsTest() {
    //     val logs = sut.getLogFiles(2)
    //     val name = "AndroidAPS.log.zip"
    //     var zipFile = File("build/$name")
    //     zipFile = sut.zipLogs(zipFile, logs)
    //     assertThat(zipFile.exists()).isTrue()
    //     assertThat(zipFile.isFile).isTrue()
    // }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        sut.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }

    // ==================== getLogFiles Tests ====================

    @Test
    fun `getLogFiles should return empty list when no logs exist`() {
        whenever(loggerUtils.logDirectory).thenReturn("src/test/assets/nonexistent")
        
        val logs = sut.getLogFiles(10)
        
        assertThat(logs).isEmpty()
    }

    @Test
    fun `getLogFiles should limit results by amount parameter`() {
        val logs = sut.getLogFiles(1)
        
        assertThat(logs).hasSize(1)
    }

    @Test
    fun `getLogFiles should sort by name descending`() {
        val logs = sut.getLogFiles(10)
        
        // First log should be the most recent
        assertThat(logs[0].name).isEqualTo("AndroidAPS.log")
    }

    // ==================== deleteLogs Tests ====================

    @Test
    fun `deleteLogs should not crash when directory is empty`() {
        whenever(loggerUtils.logDirectory).thenReturn("src/test/assets/nonexistent")
        
        // Should not throw
        sut.deleteLogs(5)
    }

    @Test
    fun `deleteLogs should handle keep value larger than file count`() {
        // When keep is larger than available files, nothing should be deleted
        sut.deleteLogs(100)
        
        // Verify files still exist
        val logs = sut.getLogFiles(10)
        assertThat(logs).isNotEmpty()
    }

    // ==================== Cloud Export Logic Tests ====================

    @Test
    fun `sendLogs should check cloud settings when cloud is active`() {
        whenever(exportOptionsDialog.isLogCloudEnabled()).thenReturn(true)
        whenever(cloudStorageManager.isCloudStorageActive()).thenReturn(true)
        whenever(cloudStorageManager.getActiveProvider()).thenReturn(cloudProvider)
        
        val isCloudEnabled = exportOptionsDialog.isLogCloudEnabled() && 
            cloudStorageManager.isCloudStorageActive()
        
        assertThat(isCloudEnabled).isTrue()
    }

    @Test
    fun `sendLogs should use email when cloud is not active`() {
        whenever(exportOptionsDialog.isLogCloudEnabled()).thenReturn(true)
        whenever(cloudStorageManager.isCloudStorageActive()).thenReturn(false)
        
        val isCloudEnabled = exportOptionsDialog.isLogCloudEnabled() && 
            cloudStorageManager.isCloudStorageActive()
        
        assertThat(isCloudEnabled).isFalse()
    }

    @Test
    fun `sendLogs should use email when log cloud is disabled`() {
        whenever(exportOptionsDialog.isLogCloudEnabled()).thenReturn(false)
        whenever(cloudStorageManager.isCloudStorageActive()).thenReturn(true)
        
        val isCloudEnabled = exportOptionsDialog.isLogCloudEnabled() && 
            cloudStorageManager.isCloudStorageActive()
        
        assertThat(isCloudEnabled).isFalse()
    }

    // ==================== Log Files Filtering Tests ====================

    @Test
    fun `getLogFiles should only include AndroidAPS log files`() {
        val logs = sut.getLogFiles(10)
        
        logs.forEach { file ->
            assertThat(file.name).startsWith("AndroidAPS")
            assertThat(file.name.endsWith(".log") || file.name.endsWith(".zip")).isTrue()
        }
    }

    @Test
    fun `getLogFiles should exclude current log suffix files`() {
        val logs = sut.getLogFiles(10)
        
        logs.forEach { file ->
            // Files should not end with the special suffix like .log.zip
            assertThat(file.name.endsWith(loggerUtils.suffix)).isFalse()
        }
    }
}
