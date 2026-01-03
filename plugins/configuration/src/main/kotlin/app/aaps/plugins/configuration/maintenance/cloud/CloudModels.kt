package app.aaps.plugins.configuration.maintenance.cloud

/**
 * Represents a file in cloud storage.
 * This is a provider-agnostic data class that can be used across different cloud services.
 */
data class CloudFile(
    /**
     * Unique identifier for this file in the cloud storage
     */
    val id: String,
    
    /**
     * File name
     */
    val name: String,
    
    /**
     * MIME type of the file
     */
    val mimeType: String,
    
    /**
     * File size in bytes
     */
    val size: Long = 0,
    
    /**
     * Creation timestamp in milliseconds since epoch
     */
    val createdTime: Long? = null,
    
    /**
     * Last modified timestamp in milliseconds since epoch
     */
    val modifiedTime: Long? = null,
    
    /**
     * Parent folder ID (if available)
     */
    val parentId: String? = null
)

/**
 * Represents a folder in cloud storage.
 * This is a provider-agnostic data class that can be used across different cloud services.
 */
data class CloudFolder(
    /**
     * Unique identifier for this folder in the cloud storage
     */
    val id: String,
    
    /**
     * Folder name
     */
    val name: String,
    
    /**
     * Parent folder ID (if available)
     */
    val parentId: String? = null
)

/**
 * Result of a file listing operation with pagination support.
 */
data class CloudFileListResult(
    /**
     * List of files returned in this page
     */
    val files: List<CloudFile>,
    
    /**
     * Token for fetching the next page, null if this is the last page
     */
    val nextPageToken: String? = null,
    
    /**
     * Total number of files (if known), -1 if unknown
     */
    val totalCount: Int = -1
)
