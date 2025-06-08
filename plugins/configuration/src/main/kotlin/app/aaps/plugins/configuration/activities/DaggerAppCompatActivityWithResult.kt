package app.aaps.plugins.configuration.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAAPSDirectorySelected
import app.aaps.core.interfaces.rx.events.EventThemeSwitch
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.maintenance.CustomWatchfaceFileContract
import app.aaps.plugins.configuration.maintenance.PrefsFileContract
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

open class DaggerAppCompatActivityWithResult : DaggerAppCompatActivity() {


    // 儲存來源型態 key
    companion object {
        const val STORAGE_TYPE_KEY = "AapsDirectoryStorageType"
        const val STORAGE_TYPE_LOCAL = "local"
        const val STORAGE_TYPE_GOOGLE_DRIVE = "google_drive"
    }

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var androidPermission: AndroidPermission

    private val compositeDisposable = CompositeDisposable()

    var accessTree: ActivityResultLauncher<Uri?>? = null
    var callForPrefFile: ActivityResultLauncher<Void?>? = null
    var callForCustomWatchfaceFile: ActivityResultLauncher<Void?>? = null
    var callForBatteryOptimization: ActivityResultLauncher<Void?>? = null
    var requestMultiplePermissions: ActivityResultLauncher<Array<String>>? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        compositeDisposable.add(rxBus.toObservable(EventThemeSwitch::class.java).subscribe {
            recreate()
        })

        accessTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val storageType = preferences.get(
                app.aaps.core.keys.SimpleString2PreferenceKey(STORAGE_TYPE_KEY, STORAGE_TYPE_LOCAL),
                ""
            )
            if (storageType == STORAGE_TYPE_LOCAL) {
                uri?.let {
                    handleDirectorySelected(it)
                }
            }
        }

        callForPrefFile = registerForActivityResult(PrefsFileContract()) {
            // Do not pass full file through intent. It crash on large file
            // it?.let {
            //     importExportPrefs.importSharedPreferences(this, it)
            // }
            importExportPrefs.doImportSharedPreferences(this)
        }
        callForCustomWatchfaceFile = registerForActivityResult(CustomWatchfaceFileContract()) { }

        callForBatteryOptimization = registerForActivityResult(OptimizationPermissionContract()) {
            updateButtons()
        }

        requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                aapsLogger.info(LTag.CORE, "Permission ${it.key} ${it.value}")
                when (it.key) {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION     ->
                        if (!it.value || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            androidPermission.notifyForLocationPermissions(this)
                            ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.location_permission_not_granted))
                        }

                    Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                        if (!it.value || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            androidPermission.notifyForLocationPermissions(this)
                            ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.location_permission_not_granted))
                        }
                }
            }
            updateButtons()
        }
    }

    /**
     * 彈出來源選擇 Dialog，讓使用者選擇「手機目錄」或「Google Drive」
     * 若選擇手機目錄，則啟動 accessTree
     * 若選擇 Google Drive，則檢查/授權並處理 Google Drive
     */
    open fun showDirectorySourceDialog() {
        val options = arrayOf(
            rh.gs(app.aaps.plugins.configuration.R.string.source_local_directory),
            rh.gs(app.aaps.plugins.configuration.R.string.source_google_drive)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(rh.gs(app.aaps.plugins.configuration.R.string.select_storage_source))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        preferences.put(
                            app.aaps.core.keys.SimpleString2PreferenceKey(STORAGE_TYPE_KEY, STORAGE_TYPE_LOCAL),
                            "",
                            STORAGE_TYPE_LOCAL
                        )
                        accessTree?.launch(null)
                    }
                    1 -> {
                        preferences.put(
                            app.aaps.core.keys.SimpleString2PreferenceKey(STORAGE_TYPE_KEY, STORAGE_TYPE_GOOGLE_DRIVE),
                            "",
                            STORAGE_TYPE_GOOGLE_DRIVE
                        )
                        handleGoogleDriveDirectory()
                    }
                }
            }
            .show()
    }

    /**
     * 處理手機目錄選擇完成後的邏輯
     */
    open fun handleDirectorySelected(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        preferences.put(StringKey.AapsDirectoryUri, uri.toString())
        rxBus.send(EventAAPSDirectorySelected(uri.path ?: "UNKNOWN"))
    }

    /**
     * Google Drive 授權與目錄選擇流程（僅預留，需串接 Google Drive API）
     */
    open fun handleGoogleDriveDirectory() {
        // Google Drive 授權流程，使用 Web client id + PKCE + local server (AppAuth)
        // 需在 build.gradle 加入 implementation 'net.openid:appauth:0.11.1'
        // 並在 Google Cloud Console 註冊 Web client id，redirect_uri 設為 http://127.0.0.1:8080/

        // val serviceConfig = net.openid.appauth.AuthorizationServiceConfiguration(
        //     Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        //     Uri.parse("https://oauth2.googleapis.com/token")
        // )
    //     val clientId = "705061051276-5ssikkg2ag39l7hj9t63saq549n3s2n5.apps.googleusercontent.com"
    //     val redirectUri = Uri.parse("http://127.0.0.1:8080/")
    //     val scope = "https://www.googleapis.com/auth/drive.file"
    //
    //     val authRequest = net.openid.appauth.AuthorizationRequest.Builder(
    //         serviceConfig,
    //         clientId,
    //         net.openid.appauth.ResponseTypeValues.CODE,
    //         redirectUri
    //     )
    //         .setScope(scope)
    //         .build()
    //
    //     if (authService == null) authService = net.openid.appauth.AuthorizationService(this)
    //     val authIntent = authService!!.getAuthorizationRequestIntent(authRequest)
    //     startActivityForResult(authIntent, 9002)
    //     // 請在 onActivityResult 處理授權結果
    // // AppAuth 物件
    // private var authService: net.openid.appauth.AuthorizationService? = null
    //
    // override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    //     super.onActivityResult(requestCode, resultCode, data)
    //     if (requestCode == 9002) {
    //         val resp = net.openid.appauth.AuthorizationResponse.fromIntent(data!!)
    //         val ex = net.openid.appauth.AuthorizationException.fromIntent(data)
    //         if (resp != null) {
    //             val tokenRequest = resp.createTokenExchangeRequest()
    //             authService?.performTokenRequest(tokenRequest) { response, exception ->
    //                 if (response != null) {
    //                     val refreshToken = response.refreshToken
    //                     if (refreshToken != null) {
    //                         preferences.put("GoogleDriveRefreshToken", refreshToken)
    //                         runOnUiThread {
    //                             ToastUtils.infoToast(this, "Google Drive 授權成功，refresh token 已取得")
    //                         }
    //                     }
    //                 } else {
    //                     runOnUiThread {
    //                         ToastUtils.errorToast(this, "Google Drive 授權失敗")
    //                     }
    //                 }
    //             }
    //         } else {
    //             ToastUtils.errorToast(this, "Google Drive 授權失敗")
    //         }
    //     }
    // }
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        accessTree = null
        callForPrefFile = null
        callForCustomWatchfaceFile = null
        callForBatteryOptimization = null
        requestMultiplePermissions = null
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    // Used for SetupWizardActivity
    open fun updateButtons() {}
}