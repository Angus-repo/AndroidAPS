package app.aaps.plugins.configuration.maintenance.googledrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.R
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveManager @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
    private val context: Context
) {
    
    companion object {
        private const val CLIENT_ID = "705061051276-3ied5cqa3kqhb0hpr7p0rggoffhq46ef.apps.googleusercontent.com"
        private const val REDIRECT_PORT = 8080
        private const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT/oauth/callback"
        private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val DRIVE_API_URL = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3"
        
        // SharedPreferences keys
        private const val PREF_GOOGLE_DRIVE_REFRESH_TOKEN = "google_drive_refresh_token"
        private const val PREF_GOOGLE_DRIVE_ACCESS_TOKEN = "google_drive_access_token"
        private const val PREF_GOOGLE_DRIVE_TOKEN_EXPIRY = "google_drive_token_expiry"
        private const val PREF_GOOGLE_DRIVE_STORAGE_TYPE = "google_drive_storage_type"
        private const val PREF_GOOGLE_DRIVE_FOLDER_ID = "google_drive_folder_id"
        
        // Storage types
        const val STORAGE_TYPE_LOCAL = "local"
        const val STORAGE_TYPE_GOOGLE_DRIVE = "google_drive"
        
        // Notification IDs
        const val NOTIFICATION_GOOGLE_DRIVE_ERROR = Notification.USER_MESSAGE + 100
    }
    
    private val client = OkHttpClient()
    private var authLauncher: ActivityResultLauncher<Intent>? = null
    
    // 錯誤狀態追蹤
    private var connectionError = false
    private var errorNotificationId: Int? = null
    
    // 本地伺服器相關
    private var localServer: ServerSocket? = null
    private var authCodeReceived: String? = null
    private var authState: String? = null
    private var serverJob: Job? = null
    
    /**
     * 檢查是否已有有效的 refresh token
     */
    fun hasValidRefreshToken(): Boolean {
        return sp.getString(PREF_GOOGLE_DRIVE_REFRESH_TOKEN, "").isNotEmpty()
    }
    
    /**
     * 獲取當前儲存類型
     */
    fun getStorageType(): String {
        return sp.getString(PREF_GOOGLE_DRIVE_STORAGE_TYPE, STORAGE_TYPE_LOCAL)
    }
    
    /**
     * 設定儲存類型
     */
    fun setStorageType(type: String) {
        sp.putString(PREF_GOOGLE_DRIVE_STORAGE_TYPE, type)
    }
    
    /**
     * 使用 PKCE 方式開始 OAuth2 認證流程
     */
    suspend fun startPKCEAuth(): String {
        return withContext(Dispatchers.IO) {
            try {
                // 啟動本地伺服器
                startLocalServer()
                
                // 生成 code verifier 和 code challenge
                val codeVerifier = generateCodeVerifier()
                val codeChallenge = generateCodeChallenge(codeVerifier)
                
                // 儲存 code verifier 供後續使用
                sp.putString("google_drive_code_verifier", codeVerifier)
                
                // 建構授權 URL
                val authUrl = buildAuthUrl(codeChallenge)
                aapsLogger.debug(LTag.CORE, "Google Drive auth URL: $authUrl")
                
                authUrl
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error starting PKCE auth", e)
                throw e
            }
        }
    }
    
    /**
     * 處理授權碼並取得 refresh token
     */
    suspend fun exchangeCodeForTokens(authCode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val codeVerifier = sp.getString("google_drive_code_verifier", "")
                if (codeVerifier.isEmpty()) {
                    throw IllegalStateException("Code verifier not found")
                }
                
                val requestBody = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("code", authCode)
                    .add("code_verifier", codeVerifier)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", REDIRECT_URI)
                    .build()
                
                val request = Request.Builder()
                    .url(TOKEN_URL)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val refreshToken = jsonResponse.optString("refresh_token")
                    val accessToken = jsonResponse.optString("access_token")
                    val expiresIn = jsonResponse.optLong("expires_in", 3600)
                    
                    if (refreshToken.isNotEmpty()) {
                        sp.putString(PREF_GOOGLE_DRIVE_REFRESH_TOKEN, refreshToken)
                        sp.putString(PREF_GOOGLE_DRIVE_ACCESS_TOKEN, accessToken)
                        sp.putLong(PREF_GOOGLE_DRIVE_TOKEN_EXPIRY, System.currentTimeMillis() + expiresIn * 1000)
                        
                        // 清除 code verifier
                        sp.remove("google_drive_code_verifier")
                        
                        aapsLogger.info(LTag.CORE, "Google Drive tokens obtained successfully")
                        return@withContext true
                    }
                }
                
                aapsLogger.error(LTag.CORE, "Failed to exchange code for tokens: $responseBody")
                false
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error exchanging code for tokens", e)
                false
            }
        }
    }
    
    /**
     * 取得有效的 access token
     */
    suspend fun getValidAccessToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = sp.getString(PREF_GOOGLE_DRIVE_ACCESS_TOKEN, "")
                val expiry = sp.getLong(PREF_GOOGLE_DRIVE_TOKEN_EXPIRY, 0)
                
                // 如果 token 還有 5 分鐘以上有效期，直接使用
                if (accessToken.isNotEmpty() && System.currentTimeMillis() < expiry - 300000) {
                    return@withContext accessToken
                }
                
                // 嘗試刷新 token
                refreshAccessToken()
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error getting valid access token", e)
                null
            }
        }
    }
    
    /**
     * 刷新 access token
     */
    private suspend fun refreshAccessToken(): String? {
        val refreshToken = sp.getString(PREF_GOOGLE_DRIVE_REFRESH_TOKEN, "")
        if (refreshToken.isEmpty()) return null
        
        try {
            val requestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()
            
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                val accessToken = jsonResponse.optString("access_token")
                val expiresIn = jsonResponse.optLong("expires_in", 3600)
                
                if (accessToken.isNotEmpty()) {
                    sp.putString(PREF_GOOGLE_DRIVE_ACCESS_TOKEN, accessToken)
                    sp.putLong(PREF_GOOGLE_DRIVE_TOKEN_EXPIRY, System.currentTimeMillis() + expiresIn * 1000)
                    return accessToken
                }
            }
            
            aapsLogger.error(LTag.CORE, "Failed to refresh access token: $responseBody")
            return null
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error refreshing access token", e)
            return null
        }
    }
    
    /**
     * 測試 Google Drive 連線
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getValidAccessToken()
                if (accessToken == null) {
                    showConnectionError("Unable to obtain a valid access token")
                    return@withContext false
                }
                val request = Request.Builder()
                    .url("$DRIVE_API_URL/about?fields=user")
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    clearConnectionError()
                    return@withContext true
                } else {
                    showConnectionError("Google Drive connection test failed")
                    return@withContext false
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error testing Google Drive connection", e)
                showConnectionError("Unable to connect to Google Drive: ${'$'}{e.message}")
                false
            }
        }
    }
    
    /**
     * 列出 Google Drive 中的資料夾
     */
    suspend fun listFolders(parentId: String = "root"): List<DriveFolder> {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getValidAccessToken()
                if (accessToken == null) {
                    showConnectionError("Unable to obtain a valid access token")
                    return@withContext emptyList()
                }
                val url = "$DRIVE_API_URL/files?q=mimeType='application/vnd.google-apps.folder' and '$parentId' in parents and trashed=false&fields=files(id,name)"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    clearConnectionError()
                    val jsonResponse = JSONObject(responseBody)
                    val files = jsonResponse.getJSONArray("files")
                    val folders = mutableListOf<DriveFolder>()
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        folders.add(DriveFolder(id = file.getString("id"), name = file.getString("name")))
                    }
                    return@withContext folders
                } else {
                    aapsLogger.error(LTag.CORE, "List folders failed: ${'$'}{response.code} ${'$'}{response.message} body=${'$'}responseBody")
                    showConnectionError("Failed to list Google Drive folders")
                    return@withContext emptyList()
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error listing Google Drive folders", e)
                showConnectionError("Error listing folders: ${'$'}{e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * 創建資料夾
     */
    suspend fun createFolder(name: String, parentId: String = "root"): String? {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getValidAccessToken() ?: return@withContext null
                
                val metadata = JSONObject().apply {
                    put("name", name)
                    put("mimeType", "application/vnd.google-apps.folder")
                    put("parents", listOf(parentId))
                }
                
                val requestBody = metadata.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$DRIVE_API_URL/files")
                    .header("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    clearConnectionError()
                    val jsonResponse = JSONObject(responseBody)
                    return@withContext jsonResponse.optString("id")
                } else {
                    aapsLogger.error(LTag.CORE, "Failed to create folder: $responseBody")
                    return@withContext null
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error creating folder", e)
                null
            }
        }
    }
    
    /**
     * Upload file to Google Drive (multipart/related)
     */
    suspend fun uploadFile(fileName: String, fileContent: ByteArray, mimeType: String = "application/octet-stream"): String? {
        return withContext(Dispatchers.IO) {
            try {
                val accessToken = getValidAccessToken()
                if (accessToken == null) {
                    showConnectionError("Unable to obtain a valid access token")
                    return@withContext null
                }
                val folderId = getSelectedFolderId().ifEmpty { "root" }

                // Metadata JSON body with its own Content-Type
                val metadataJson = JSONObject().apply {
                    put("name", fileName)
                    put("parents", JSONArray().put(folderId))
                }.toString()
                val metadataBody = metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())

                // File body with its own Content-Type
                val mediaBody = fileContent.toRequestBody(mimeType.toMediaType())

                // Important: Do NOT add "Content-Type" as a header in addPart(); OkHttp forbids it.
                val multipart = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(metadataBody)
                    .addPart(mediaBody)
                    .build()

                val request = Request.Builder()
                    .url("$UPLOAD_URL/files?uploadType=multipart&fields=id")
                    .header("Authorization", "Bearer $accessToken")
                    .post(multipart)
                    .build()

                val response = client.newCall(request).execute()
                val responseBodyStr = response.body?.string() ?: ""

                return@withContext if (response.isSuccessful) {
                    clearConnectionError()
                    val jsonResponse = JSONObject(responseBodyStr.ifEmpty { "{}" })
                    jsonResponse.optString("id").takeIf { it.isNotEmpty() }
                } else {
                    aapsLogger.error(LTag.CORE, "Drive upload failed: ${'$'}{response.code} ${'$'}{response.message} body=${'$'}responseBodyStr")
                    showConnectionError("Upload failed: ${'$'}{response.code}")
                    null
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Error uploading file to Google Drive", e)
                showConnectionError("Error uploading file: ${'$'}{e.message}")
                null
            }
        }
    }

    /**
     * 設定選定的資料夾 ID
     */
    fun setSelectedFolderId(folderId: String) {
        sp.putString(PREF_GOOGLE_DRIVE_FOLDER_ID, folderId)
    }
    
    /**
     * 取得選定的資料夾 ID
     */
    fun getSelectedFolderId(): String {
        return sp.getString(PREF_GOOGLE_DRIVE_FOLDER_ID, "")
    }
    
    /**
     * 清除 Google Drive 相關設定
     */
    fun clearGoogleDriveSettings() {
        sp.remove(PREF_GOOGLE_DRIVE_REFRESH_TOKEN)
        sp.remove(PREF_GOOGLE_DRIVE_ACCESS_TOKEN)
        sp.remove(PREF_GOOGLE_DRIVE_TOKEN_EXPIRY)
        sp.remove(PREF_GOOGLE_DRIVE_FOLDER_ID)
        sp.remove("google_drive_code_verifier")
    }
    
    /**
     * 顯示連線錯誤通知
     */
    private fun showConnectionError(message: String) {
        connectionError = true
        val notificationId = NOTIFICATION_GOOGLE_DRIVE_ERROR
        errorNotificationId = notificationId
        
        val notification = Notification(
            notificationId,
            message,
            Notification.URGENT,
            60
        )
        rxBus.send(EventNewNotification(notification))
    }
    

    
    /**
     * 生成 code verifier
     */
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    /**
     * 生成 code challenge
     */
    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
    
    /**
     * 建構授權 URL
     */
    private fun buildAuthUrl(codeChallenge: String): String {
        val state = UUID.randomUUID().toString()
        sp.putString("google_drive_oauth_state", state)
        
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
            .toString()
    }
    
    /**
     * 啟動本地 HTTP 伺服器來接收 OAuth 回調
     */
    private fun startLocalServer() {
        try {
            // 停止現有的伺服器
            stopLocalServer()
            
            // 創建新的伺服器
            localServer = ServerSocket(REDIRECT_PORT)
            localServer?.soTimeout = 1000  // 設置超時以避免永久阻塞
            
            // 啟動伺服器處理請求
            serverJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    aapsLogger.debug(LTag.CORE, "Local OAuth server started on port $REDIRECT_PORT")

                    val server = localServer ?: return@launch
                    while (isActive && !server.isClosed) {
                        try {
                            val clientSocket = try { server.accept() } catch (toe: java.net.SocketTimeoutException) { null }
                            clientSocket?.let { socket ->
                                launch { handleHttpRequest(socket) }
                            }
                        } catch (e: Exception) {
                            if (!server.isClosed) {
                                aapsLogger.error(LTag.CORE, "Error accepting connection", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    val server = localServer
                    if (server != null && !server.isClosed) {
                        aapsLogger.error(LTag.CORE, "Server error", e)
                    }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Failed to start local OAuth server", e)
            throw e
        }
    }
    
    /**
     * 停止本地 HTTP 伺服器
     */
    private fun stopLocalServer() {
        try {
            serverJob?.cancel()
            localServer?.close()
            localServer = null
            aapsLogger.debug(LTag.CORE, "Local OAuth server stopped")
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error stopping server", e)
        }
    }
    
    /**
     * 處理 HTTP 請求
     */
    private suspend fun handleHttpRequest(socket: Socket) {
        try {
            val input = socket.getInputStream().bufferedReader()
            val output = socket.getOutputStream()
            
            // 讀取 HTTP 請求
            val requestLine = input.readLine()
            if (requestLine == null) {
                socket.close()
                return
            }
            
            aapsLogger.debug(LTag.CORE, "HTTP Request: $requestLine")
            
            // 解析請求路徑
            val parts = requestLine.split(" ")
            if (parts.size >= 2) {
                val path = parts[1]
                if (path.startsWith("/oauth/callback")) {
                    handleOAuthCallback(path, output)
                } else {
                    sendHttpResponse(output, 404, "Not Found")
                }
            } else {
                sendHttpResponse(output, 400, "Bad Request")
            }
            
            socket.close()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error handling HTTP request", e)
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }
    
    /**
     * 處理 OAuth 回調
     */
    private suspend fun handleOAuthCallback(path: String, output: OutputStream) {
        try {
            // 解析查詢參數
            val queryIndex = path.indexOf('?')
            val params = if (queryIndex >= 0) {
                parseQueryString(path.substring(queryIndex + 1))
            } else {
                emptyMap()
            }
            
            val code = params["code"]
            val state = params["state"]
            val error = params["error"]
            
            aapsLogger.debug(LTag.CORE, "OAuth callback received - code: ${code != null}, state: $state, error: $error")
            
            if (error != null) {
                sendHttpResponse(output, 400, "OAuth error: $error")
                return
            }
            
            if (code != null && state != null) {
                // 驗證 state
                val savedState = sp.getString("google_drive_oauth_state", "")
                if (state == savedState) {
                    authCodeReceived = code
                    authState = state
                    sendHttpResponse(output, 200, "Authorization successful! You can close this window.")
                } else {
                    sendHttpResponse(output, 400, "Invalid state parameter")
                }
            } else {
                sendHttpResponse(output, 400, "Missing code or state parameter")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error handling OAuth callback", e)
            sendHttpResponse(output, 500, "Internal server error")
        } finally {
            // 延遲關閉伺服器
            CoroutineScope(Dispatchers.IO).launch {
                delay(2000) // 等待2秒讓回應完成
                stopLocalServer()
            }
        }
    }
    
    /**
     * 解析查詢字串
     */
    private fun parseQueryString(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        
        return query.split("&").associate { param ->
            val keyValue = param.split("=", limit = 2)
            val key = keyValue[0]
            val value = if (keyValue.size > 1) keyValue[1] else ""
            key to value
        }
    }
    
    /**
     * 發送 HTTP 回應
     */
    private fun sendHttpResponse(output: OutputStream, statusCode: Int, message: String) {
        try {
            val statusText = when (statusCode) {
                200 -> "OK"
                400 -> "Bad Request"
                404 -> "Not Found"
                500 -> "Internal Server Error"
                else -> "Unknown"
            }
            val autoCloseScript = if (statusCode == 200) """
                <script>
                    (function() {
                        function tryClose() {
                            try { window.close(); } catch (e) {}
                            if (!window.closed) {
                                try { window.open('', '_self'); window.close(); } catch (e) {}
                            }
                            if (!window.closed) {
                                try { history.go(-1); } catch (e) {}
                            }
                            if (!window.closed) {
                                try { location.replace('about:blank'); } catch (e) {}
                            }
                        }
                        // 嘗試立即關閉，再做幾次退避
                        tryClose();
                        setTimeout(tryClose, 300);
                        setTimeout(tryClose, 800);
                        setTimeout(tryClose, 1500);
                    })();
                </script>
            """ else ""
            val className = if (statusCode == 200) "success" else "error"
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>AAPS Google Drive Authorization</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <style>
                        body { font-family: Arial, sans-serif; text-align: center; margin-top: 50px; }
                        .success { color: green; }
                        .error { color: red; }
                    </style>
                </head>
                <body>
                    <h1>AAPS Google Drive Authorization</h1>
                    <p class="$className">$message</p>
                    $autoCloseScript
                </body>
                </html>
            """.trimIndent()
            val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                          "Content-Type: text/html; charset=UTF-8\r\n" +
                          "Content-Length: ${htmlContent.toByteArray().size}\r\n" +
                          "Connection: close\r\n" +
                          "\r\n" +
                          htmlContent
            output.write(response.toByteArray())
            output.flush()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error sending HTTP response", e)
        }
    }
    
    /**
     * 等待並取得授權碼
     */
    suspend fun waitForAuthCode(timeoutMs: Long = 60000): String? {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            while (authCodeReceived == null && System.currentTimeMillis() - startTime < timeoutMs) {
                delay(500)
            }
            
            val code = authCodeReceived
            authCodeReceived = null // 清除已使用的授權碼
            authState = null
            
            code
        }
    }
    
    /**
     * 檢查是否有連接錯誤
     */
    fun hasConnectionError(): Boolean {
        val storageType = sp.getString(PREF_GOOGLE_DRIVE_STORAGE_TYPE, STORAGE_TYPE_LOCAL)
        return storageType == STORAGE_TYPE_GOOGLE_DRIVE && connectionError
    }
    
    /**
     * 清除連接錯誤狀態
     */
    fun clearConnectionError() {
        connectionError = false
        errorNotificationId?.let { id ->
            // TODO: 實作清除通知的邏輯
        }
        errorNotificationId = null
    }

    /**
     * 列出目前資料夾中的設定檔(json)
     */
    suspend fun listSettingsFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getValidAccessToken() ?: return@withContext emptyList()
            val folderId = getSelectedFolderId().ifEmpty { "root" }
            val query = "'$folderId' in parents and trashed=false and mimeType != 'application/vnd.google-apps.folder' and name contains '.json'"
            val url = "$DRIVE_API_URL/files?q=${Uri.encode(query)}&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc&pageSize=50"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                aapsLogger.error(LTag.CORE, "Failed to list settings files: $body")
                showConnectionError("Failed to list settings files")
                return@withContext emptyList()
            }
            clearConnectionError()
            val json = JSONObject(body)
            val arr = json.optJSONArray("files") ?: JSONArray()
            val result = mutableListOf<DriveFile>()
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                result.add(DriveFile(id = f.getString("id"), name = f.getString("name")))
            }
            result
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error listing settings files", e)
            showConnectionError("Error listing settings files: ${e.message}")
            emptyList()
        }
    }

    /**
     * 下載檔案內容
     */
    suspend fun downloadFile(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val accessToken = getValidAccessToken() ?: return@withContext null
            val request = Request.Builder()
                .url("$DRIVE_API_URL/files/${'$'}fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val msg = response.body?.string()
                aapsLogger.error(LTag.CORE, "Failed to download file: ${'$'}msg")
                showConnectionError("Failed to download file")
                return@withContext null
            }
            clearConnectionError()
            response.body?.bytes()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Error downloading file", e)
            showConnectionError("Error downloading file: ${'$'}{e.message}")
            null
        }
    }
}

/**
 * Google Drive 資料夾資料類別
 */
data class DriveFolder(
    val id: String,
    val name: String
)

/** 新增：Google Drive 檔案資料類別 */
data class DriveFile(
    val id: String,
    val name: String
)
