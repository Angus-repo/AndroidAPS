package app.aaps.plugins.configuration.maintenance.storage

/**
 * Storage type constants for backup and export functionality
 * 
 * This object centralizes all storage type definitions to support
 * future expansion with additional cloud storage providers.
 */
object StorageType {
    /**
     * Local storage - files are stored on device's local storage
     */
    const val LOCAL = "local"
    
    /**
     * Google Drive cloud storage
     */
    const val GOOGLE_DRIVE = "google_drive"
    
    // Future cloud storage providers can be added here:
    // const val ONEDRIVE = "onedrive"
    // const val DROPBOX = "dropbox"
    // const val AWS_S3 = "aws_s3"
    // const val AZURE_BLOB = "azure_blob"
    
    /**
     * Get all available storage types
     */
    val ALL_TYPES = listOf(LOCAL, GOOGLE_DRIVE)
    
    /**
     * Get all cloud storage types (excluding local)
     */
    val CLOUD_TYPES = listOf(GOOGLE_DRIVE)
    
    /**
     * Check if the given storage type is a cloud storage type
     */
    fun isCloudStorage(storageType: String): Boolean {
        return storageType in CLOUD_TYPES
    }
    
    /**
     * Check if the given storage type is valid
     */
    fun isValidStorageType(storageType: String): Boolean {
        return storageType in ALL_TYPES
    }
}
