package app.aaps.configuration.maintenance.cloud

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageManager
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageProvider
import app.aaps.plugins.configuration.maintenance.cloud.StorageTypes
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for CloudStorageManager
 */
class CloudStorageManagerTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var mockProvider: CloudStorageProvider

    private lateinit var sut: CloudStorageManager

    @BeforeEach
    fun setup() {
        // Mock the provider storage type
        whenever(mockProvider.storageType).thenReturn(StorageTypes.GOOGLE_DRIVE)
        
        // Use Set<CloudStorageProvider> for multi-binding style
        sut = CloudStorageManager(
            aapsLogger = aapsLogger,
            sp = sp,
            cloudStorageProviders = setOf(mockProvider)
        )
    }

    // ==================== Provider Registration Tests ====================

    @Test
    fun `getAvailableProviders should return all registered providers`() {
        val providers = sut.getAvailableProviders()
        
        assertThat(providers).hasSize(1)
        assertThat(providers[0].storageType).isEqualTo(StorageTypes.GOOGLE_DRIVE)
    }

    @Test
    fun `getProvider should return correct provider for storage type`() {
        val provider = sut.getProvider(StorageTypes.GOOGLE_DRIVE)
        
        assertThat(provider).isNotNull()
        assertThat(provider?.storageType).isEqualTo(StorageTypes.GOOGLE_DRIVE)
    }

    @Test
    fun `getProvider should return null for unknown storage type`() {
        val provider = sut.getProvider("unknown_type")
        
        assertThat(provider).isNull()
    }

    // ==================== Active Provider Tests ====================

    @Test
    fun `getActiveProvider should return null when local storage is active`() {
        whenever(sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL))
            .thenReturn(StorageTypes.LOCAL)
        
        val provider = sut.getActiveProvider()
        
        assertThat(provider).isNull()
    }

    @Test
    fun `getActiveProvider should return google drive when google drive is active`() {
        whenever(sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL))
            .thenReturn(StorageTypes.GOOGLE_DRIVE)
        
        val provider = sut.getActiveProvider()
        
        assertThat(provider).isNotNull()
        assertThat(provider?.storageType).isEqualTo(StorageTypes.GOOGLE_DRIVE)
    }

    // ==================== Storage Type Tests ====================

    @Test
    fun `getActiveStorageType should return stored value`() {
        whenever(sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL))
            .thenReturn(StorageTypes.GOOGLE_DRIVE)
        
        val storageType = sut.getActiveStorageType()
        
        assertThat(storageType).isEqualTo(StorageTypes.GOOGLE_DRIVE)
    }

    @Test
    fun `getActiveStorageType should return LOCAL by default`() {
        whenever(sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL))
            .thenReturn(StorageTypes.LOCAL)
        
        val storageType = sut.getActiveStorageType()
        
        assertThat(storageType).isEqualTo(StorageTypes.LOCAL)
    }

    @Test
    fun `isCloudStorageActive should return false when local is active`() {
        whenever(sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL))
            .thenReturn(StorageTypes.LOCAL)
        
        assertThat(sut.isCloudStorageActive()).isFalse()
    }

    @Test
    fun `isCloudStorageActive should return true when google drive is active`() {
        whenever(sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL))
            .thenReturn(StorageTypes.GOOGLE_DRIVE)
        
        assertThat(sut.isCloudStorageActive()).isTrue()
    }

    // ==================== Credentials Tests ====================

    @Test
    fun `hasAnyCloudCredentials should return false when no provider has credentials`() {
        whenever(mockProvider.hasValidCredentials()).thenReturn(false)
        
        assertThat(sut.hasAnyCloudCredentials()).isFalse()
    }

    @Test
    fun `hasAnyCloudCredentials should return true when provider has credentials`() {
        whenever(mockProvider.hasValidCredentials()).thenReturn(true)
        
        assertThat(sut.hasAnyCloudCredentials()).isTrue()
    }

    @Test
    fun `getAuthenticatedProviders should return only providers with valid credentials`() {
        whenever(mockProvider.hasValidCredentials()).thenReturn(true)
        
        val authenticatedProviders = sut.getAuthenticatedProviders()
        
        assertThat(authenticatedProviders).hasSize(1)
    }

    @Test
    fun `getAuthenticatedProviders should return empty when no provider has credentials`() {
        whenever(mockProvider.hasValidCredentials()).thenReturn(false)
        
        val authenticatedProviders = sut.getAuthenticatedProviders()
        
        assertThat(authenticatedProviders).isEmpty()
    }

    // ==================== Connection Error Tests ====================

    @Test
    fun `hasConnectionError should return false when no active provider`() {
        whenever(sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL))
            .thenReturn(StorageTypes.LOCAL)
        
        assertThat(sut.hasConnectionError()).isFalse()
    }

    @Test
    fun `hasConnectionError should return provider connection error status`() {
        whenever(sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL))
            .thenReturn(StorageTypes.GOOGLE_DRIVE)
        whenever(mockProvider.hasConnectionError()).thenReturn(true)
        
        assertThat(sut.hasConnectionError()).isTrue()
    }
}
