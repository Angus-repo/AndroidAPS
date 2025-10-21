# AAPSClient Variant 擴充規格文件

## 概述

本文件詳細說明如何在 AndroidAPS 專案中新增額外的 AAPSClient variant（例如 aapsclient3, aapsclient4 等），使您可以在同一裝置上安裝多個獨立的 AAPSClient 應用程式。

**當前狀態：** 支援 aapsclient, aapsclient2, aapsclient3, aapsclient4, aapsclient5, aapsclient6

## 前置需求

- 為每個新 variant 準備對應的應用程式圖示（PNG 格式，不同解析度）
- 了解 Android Gradle 建置系統基本概念
- 有 Firebase 專案存取權限（用於更新 google-services.json）

## 步驟一：準備應用程式圖示

### 1.1 圖示命名規範

為新的 variant 準備圖示，命名格式：`ic_[顏色]owl.png`

**範例：**
- aapsclient3: `ic_greenowl.png`
- aapsclient4: `ic_purpleowl.png`
- aapsclient5: `ic_darkbuleowl.png`（注意拼字）
- aapsclient6: `ic_grayowl.png`

### 1.2 圖示尺寸要求

需要為以下解析度準備圖示：
- `mipmap-mdpi/`: 48x48 px
- `mipmap-hdpi/`: 72x72 px
- `mipmap-xhdpi/`: 96x96 px
- `mipmap-xxhdpi/`: 144x144 px
- `mipmap-xxxhdpi/`: 192x192 px

### 1.3 放置圖示檔案

將圖示放置於：
```
core/ui/src/main/res/mipmap-[density]/ic_[顏色]owl.png
```

**範例檔案結構：**
```
core/ui/src/main/res/
├── mipmap-hdpi/
│   ├── ic_greenowl.png
│   ├── ic_purpleowl.png
│   ├── ic_darkbuleowl.png
│   └── ic_grayowl.png
├── mipmap-mdpi/
│   ├── ic_greenowl.png
│   └── ...
├── mipmap-xhdpi/
├── mipmap-xxhdpi/
└── mipmap-xxxhdpi/
```

## 步驟二：更新核心配置檔案

### 2.1 更新 Config 介面

**檔案位置：** `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/configuration/Config.kt`

**修改內容：** 在介面中新增新的 variant 屬性

```kotlin
interface Config {
    // ... 現有屬性 ...
    val AAPSCLIENT: Boolean
    val AAPSCLIENT1: Boolean
    val AAPSCLIENT2: Boolean
    
    // 新增以下屬性
    val AAPSCLIENT3: Boolean
    val AAPSCLIENT4: Boolean
    val AAPSCLIENT5: Boolean
    val AAPSCLIENT6: Boolean
    // 可繼續新增 AAPSCLIENT7, AAPSCLIENT8 等
    
    val PUMPCONTROL: Boolean
    // ... 其他屬性 ...
}
```

### 2.2 實作 Config 介面

**檔案位置：** `app/src/main/kotlin/app/aaps/implementations/ConfigImpl.kt`

**修改內容：** 實作新的 variant 屬性並更新 AAPSCLIENT 定義

找到並修改以下區塊：

```kotlin
override val SUPPORTED_NS_VERSION = 150000 // 15.0.0
override val APS = BuildConfig.FLAVOR == "full"

// 更新 AAPSCLIENT 定義，包含所有 variant
override val AAPSCLIENT = BuildConfig.FLAVOR == "aapsclient" || 
                          BuildConfig.FLAVOR == "aapsclient2" || 
                          BuildConfig.FLAVOR == "aapsclient3" || 
                          BuildConfig.FLAVOR == "aapsclient4" || 
                          BuildConfig.FLAVOR == "aapsclient5" || 
                          BuildConfig.FLAVOR == "aapsclient6"
                          // 新增更多 variant 時在此處加入

// 個別 variant 定義
override val AAPSCLIENT1 = BuildConfig.FLAVOR == "aapsclient"
override val AAPSCLIENT2 = BuildConfig.FLAVOR == "aapsclient2"
override val AAPSCLIENT3 = BuildConfig.FLAVOR == "aapsclient3"
override val AAPSCLIENT4 = BuildConfig.FLAVOR == "aapsclient4"
override val AAPSCLIENT5 = BuildConfig.FLAVOR == "aapsclient5"
override val AAPSCLIENT6 = BuildConfig.FLAVOR == "aapsclient6"
// 新增更多 variant 時在此處加入

override val PUMPCONTROL = BuildConfig.FLAVOR == "pumpcontrol"
override val PUMPDRIVERS = BuildConfig.FLAVOR == "full" || BuildConfig.FLAVOR == "pumpcontrol"
```

### 2.3 更新圖示提供者

**檔案位置：** `implementation/src/main/kotlin/app/aaps/implementation/resources/IconsProviderImplementation.kt`

**修改內容：** 新增圖示映射

```kotlin
override fun getIcon(): Int =
    when {
        config.AAPSCLIENT6 -> app.aaps.core.ui.R.mipmap.ic_grayowl
        config.AAPSCLIENT5 -> app.aaps.core.ui.R.mipmap.ic_darkbuleowl
        config.AAPSCLIENT4 -> app.aaps.core.ui.R.mipmap.ic_purpleowl
        config.AAPSCLIENT3 -> app.aaps.core.ui.R.mipmap.ic_greenowl
        config.AAPSCLIENT2 -> app.aaps.core.ui.R.mipmap.ic_blueowl
        config.AAPSCLIENT1 -> app.aaps.core.ui.R.mipmap.ic_yellowowl
        // 新增更多 variant 時在此處加入（注意順序，從新到舊）
        config.PUMPCONTROL -> app.aaps.core.ui.R.mipmap.ic_pumpcontrol
        else               -> app.aaps.core.ui.R.mipmap.ic_launcher
    }
```

**注意事項：**
- 順序很重要：新的 variant 應該放在前面
- 圖示名稱必須與實際檔案名稱完全一致
- 使用 `app.aaps.core.ui.R.mipmap.` 前綴

## 步驟三：配置 Gradle 建置系統

### 3.1 更新 App 模組配置

**檔案位置：** `app/build.gradle.kts`

**修改內容：** 在 `productFlavors` 區塊中新增 variant

找到 `productFlavors` 區塊（通常在 `android { }` 內），新增：

```kotlin
android {
    // ... 其他配置 ...
    
    flavorDimensions.add("standard")
    productFlavors {
        create("full") { /* ... */ }
        create("pumpcontrol") { /* ... */ }
        create("aapsclient") { /* ... */ }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient2")
            versionName = Versions.appVersion + "-aapsclient2"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_blueowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_blueowl"
        }
        
        // 新增以下 variants
        create("aapsclient3") {
            applicationId = "info.nightscout.aapsclient3"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient3")
            versionName = Versions.appVersion + "-aapsclient3"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_greenowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_greenowl"
        }
        create("aapsclient4") {
            applicationId = "info.nightscout.aapsclient4"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient4")
            versionName = Versions.appVersion + "-aapsclient4"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_purpleowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_purpleowl"
        }
        create("aapsclient5") {
            applicationId = "info.nightscout.aapsclient5"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient5")
            versionName = Versions.appVersion + "-aapsclient5"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_darkblueowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_darkblueowl"
        }
        create("aapsclient6") {
            applicationId = "info.nightscout.aapsclient6"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient6")
            versionName = Versions.appVersion + "-aapsclient6"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_grayowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_grayowl"
        }
        // 繼續新增更多 variants...
    }
}
```

**配置說明：**
- `applicationId`: 每個 variant 必須唯一，格式為 `info.nightscout.aapsclientX`
- `app_name`: 應用程式顯示名稱，建議使用 `AAPSClientX` 格式
- `versionName`: 版本名稱後綴，使用 `-aapsclientX` 格式
- `manifestPlaceholders`: 設定應用程式圖示

### 3.2 更新模組依賴配置

**檔案位置：** `buildSrc/src/main/kotlin/android-module-dependencies.gradle.kts`

**修改內容：** 在所有 library modules 中新增相同的 flavors

```kotlin
android {
    // ... 其他配置 ...
    
    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            isDefault = true
            dimension = "standard"
        }
        create("pumpcontrol") {
            dimension = "standard"
        }
        create("aapsclient") {
            dimension = "standard"
        }
        create("aapsclient2") {
            dimension = "standard"
        }
        
        // 新增以下 flavors
        create("aapsclient3") {
            dimension = "standard"
        }
        create("aapsclient4") {
            dimension = "standard"
        }
        create("aapsclient5") {
            dimension = "standard"
        }
        create("aapsclient6") {
            dimension = "standard"
        }
        // 繼續新增更多 flavors...
    }
}
```

**重要說明：**
- 此檔案定義了所有 library modules 的共同 flavor 配置
- 所有在 `app/build.gradle.kts` 中定義的 flavors 都必須在此處同步新增
- 僅需指定 `dimension`，不需要其他配置

### 3.3 更新 Wear 模組配置（如果使用）

**檔案位置：** `wear/build.gradle.kts`

**修改內容：** 新增 Wear 應用的 variant 配置

```kotlin
android {
    // ... 其他配置 ...
    
    flavorDimensions.add("standard")
    productFlavors {
        create("full") { /* ... */ }
        create("pumpcontrol") { /* ... */ }
        create("aapsclient") { /* ... */ }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            versionName = Versions.appVersion + "-aapsclient2"
        }
        
        // 新增以下 variants
        create("aapsclient3") {
            applicationId = "info.nightscout.aapsclient3"
            dimension = "standard"
            versionName = Versions.appVersion + "-aapsclient3"
        }
        create("aapsclient4") {
            applicationId = "info.nightscout.aapsclient4"
            dimension = "standard"
            versionName = Versions.appVersion + "-aapsclient4"
        }
        create("aapsclient5") {
            applicationId = "info.nightscout.aapsclient5"
            dimension = "standard"
            versionName = Versions.appVersion + "-aapsclient5"
        }
        create("aapsclient6") {
            applicationId = "info.nightscout.aapsclient6"
            dimension = "standard"
            versionName = Versions.appVersion + "-aapsclient6"
        }
        // 繼續新增更多 variants...
    }
}
```

## 步驟四：配置 Firebase Google Services

### 4.1 更新 google-services.json

**檔案位置：** `app/google-services.json`

**修改內容：** 在 `client` 陣列中新增每個新 variant 的配置

找到 `"client": [` 陣列，新增以下配置（在 aapsclient2 之後，pumpcontrol 之前）：

```json
{
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:477603612366:android:56b8fa16651fb8dbfcb29f",
        "android_client_info": {
          "package_name": "info.nightscout.aapsclient"
        }
      },
      "oauth_client": [
        {
          "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyD9HRtJJsnk_SbAMAuvudSua2vEm3j3430"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:477603612366:android:8e749a9253635bcafcb29f",
        "android_client_info": {
          "package_name": "info.nightscout.aapsclient2"
        }
      },
      "oauth_client": [
        {
          "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyD9HRtJJsnk_SbAMAuvudSua2vEm3j3430"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    },
    
    // 新增以下配置
    {
      "client_info": {
        "mobilesdk_app_id": "1:477603612366:android:aapsclient3fcb29f",
        "android_client_info": {
          "package_name": "info.nightscout.aapsclient3"
        }
      },
      "oauth_client": [
        {
          "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyD9HRtJJsnk_SbAMAuvudSua2vEm3j3430"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:477603612366:android:aapsclient4fcb29f",
        "android_client_info": {
          "package_name": "info.nightscout.aapsclient4"
        }
      },
      "oauth_client": [
        {
          "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyD9HRtJJsnk_SbAMAuvudSua2vEm3j3430"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:477603612366:android:aapsclient5fcb29f",
        "android_client_info": {
          "package_name": "info.nightscout.aapsclient5"
        }
      },
      "oauth_client": [
        {
          "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyD9HRtJJsnk_SbAMAuvudSua2vEm3j3430"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:477603612366:android:aapsclient6fcb29f",
        "android_client_info": {
          "package_name": "info.nightscout.aapsclient6"
        }
      },
      "oauth_client": [
        {
          "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyD9HRtJJsnk_SbAMAuvudSua2vEm3j3430"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "477603612366-a925drvlvs7qn7gt73r585erbqto8c79.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    },
    // 繼續新增更多 clients...
    
    {
      "client_info": {
        "mobilesdk_app_id": "1:477603612366:android:aef229914e3e5448",
        "android_client_info": {
          "package_name": "info.nightscout.aapspumpcontrol"
        }
      },
      // ... pumpcontrol 配置 ...
    }
    // ... 其他現有 clients ...
  ]
}
```

**配置說明：**
- `package_name`: 必須與 `app/build.gradle.kts` 中的 `applicationId` 完全一致
- `mobilesdk_app_id`: 可使用格式 `1:477603612366:android:aapsclientXfcb29f`（X 為編號）
- 其他欄位（oauth_client, api_key, services）可複製現有配置
- 確保 JSON 格式正確（逗號、括號等）

### 4.2 在 Firebase Console 中註冊（可選但建議）

1. 登入 [Firebase Console](https://console.firebase.google.com/)
2. 選擇您的專案（例如：androidaps-c34f8）
3. 進入 Project Settings
4. 在 "Your apps" 區塊中點擊 "Add app"
5. 選擇 Android 平台
6. 輸入 package name（例如：info.nightscout.aapsclient3）
7. 下載新的 `google-services.json` 並合併到專案中

## 步驟五：建置和測試

### 5.1 清理專案

```bash
./gradlew clean
```

### 5.2 建置未簽名版本（測試用）

```bash
./gradlew :app:assembleAapsclient3Release
```

### 5.3 建置已簽名版本（正式發布）

```bash
./gradlew :app:assembleAapsclient3Release \
  -Pandroid.injected.signing.store.file=/path/to/your/keystore.jks \
  -Pandroid.injected.signing.store.password=your_store_password \
  -Pandroid.injected.signing.key.alias=your_key_alias \
  -Pandroid.injected.signing.key.password=your_key_password
```

**輸出位置：**
- 未簽名：`app/build/outputs/apk/aapsclient3/release/app-aapsclient3-release-unsigned.apk`
- 已簽名：`app/build/outputs/apk/aapsclient3/release/app-aapsclient3-release.apk`

### 5.4 安裝到裝置

使用 adb 安裝：
```bash
adb install app/build/outputs/apk/aapsclient3/release/app-aapsclient3-release.apk
```

或替換為其他 variant 編號（aapsclient4, aapsclient5 等）。

### 5.5 驗證安裝

檢查已安裝的應用程式：
```bash
adb shell pm list packages | grep nightscout
```

應該會看到：
```
package:info.nightscout.aapsclient
package:info.nightscout.aapsclient2
package:info.nightscout.aapsclient3
package:info.nightscout.aapsclient4
package:info.nightscout.aapsclient5
package:info.nightscout.aapsclient6
```

## 故障排除

### 問題 1: Data Binding 錯誤

**錯誤訊息：**
```
error: cannot find symbol
public class Combov2FragmentBindingImpl extends Combov2FragmentBinding
```

**解決方案：**
- 確認已在 `ConfigImpl.kt` 中實作所有新的 `AAPSCLIENTX` 屬性
- 執行 `./gradlew clean` 清理建置快取

### 問題 2: Variant 不匹配錯誤

**錯誤訊息：**
```
No matching variant found for configuration
```

**解決方案：**
- 確認 `buildSrc/src/main/kotlin/android-module-dependencies.gradle.kts` 中有對應的 flavor
- 確認所有模組的 flavor dimension 都是 "standard"

### 問題 3: Google Services 配置錯誤

**錯誤訊息：**
```
No matching client found for package name 'info.nightscout.aapsclient3'
```

**解決方案：**
- 檢查 `app/google-services.json` 中是否有對應的 package_name 配置
- 確認 package_name 與 `app/build.gradle.kts` 中的 applicationId 一致

### 問題 4: 安裝失敗（無簽名）

**錯誤訊息：**
```
INSTALL_PARSE_FAILED_NO_CERTIFICATES
```

**解決方案：**
- 使用簽名版本建置（參考步驟 5.3）
- 確保使用正確的 keystore 和密碼

### 問題 5: Gradle 快取問題

**錯誤訊息：**
```
Failed to create MD5 hash for file
```

**解決方案：**
```bash
./gradlew --stop
rm -rf build core/*/build buildSrc/.kotlin
./gradlew clean
```

## 批次建置所有 Variants

### 建置腳本範例（build_all_aapsclients.sh）

```bash
#!/bin/bash

# 配置
KEYSTORE="/path/to/your/keystore.jks"
STORE_PASSWORD="your_store_password"
KEY_ALIAS="your_key_alias"
KEY_PASSWORD="your_key_password"

# Variants 列表
VARIANTS=("aapsclient" "aapsclient2" "aapsclient3" "aapsclient4" "aapsclient5" "aapsclient6")

# 清理
echo "清理專案..."
./gradlew clean

# 建置所有 variants
for variant in "${VARIANTS[@]}"
do
    echo "建置 $variant..."
    ./gradlew ":app:assemble${variant^}Release" \
        -Pandroid.injected.signing.store.file="$KEYSTORE" \
        -Pandroid.injected.signing.store.password="$STORE_PASSWORD" \
        -Pandroid.injected.signing.key.alias="$KEY_ALIAS" \
        -Pandroid.injected.signing.key.password="$KEY_PASSWORD"
    
    if [ $? -eq 0 ]; then
        echo "✓ $variant 建置成功"
    else
        echo "✗ $variant 建置失敗"
        exit 1
    fi
done

echo "所有 variants 建置完成！"
echo "APK 位置: app/build/outputs/apk/"
```

使用方式：
```bash
chmod +x build_all_aapsclients.sh
./build_all_aapsclients.sh
```

## 進階配置

### 自訂應用程式名稱字串資源

如需為不同 variant 提供完全自訂的應用程式名稱，可在各 variant 的資源目錄中建立：

```
app/src/aapsclient3/res/values/strings.xml
```

內容：
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">我的 AAPSClient 3</string>
</resources>
```

### 自訂主題顏色

在各 variant 的資源目錄中建立：

```
app/src/aapsclient3/res/values/colors.xml
```

內容：
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#4CAF50</color>
    <color name="primaryDark">#388E3C</color>
    <color name="accent">#FF5722</color>
</resources>
```

## 版本控制建議

### Git 提交訊息範例

```
feat: 新增 aapsclient3-6 variants

- 新增 aapsclient3, 4, 5, 6 四個新的應用程式 variants
- 為每個 variant 準備對應圖示（綠色、紫色、深藍、灰色貓頭鷹）
- 更新 Config 介面和實作
- 配置 Gradle flavors 和 Firebase Google Services
- 更新圖示提供者映射

變更檔案：
- core/interfaces/src/main/kotlin/app/aaps/core/interfaces/configuration/Config.kt
- app/src/main/kotlin/app/aaps/implementations/ConfigImpl.kt
- implementation/src/main/kotlin/app/aaps/implementation/resources/IconsProviderImplementation.kt
- app/build.gradle.kts
- buildSrc/src/main/kotlin/android-module-dependencies.gradle.kts
- wear/build.gradle.kts
- app/google-services.json
- core/ui/src/main/res/mipmap-*/ic_*owl.png
```

### .gitignore 建議

確保以下內容在 `.gitignore` 中：
```
# Build 輸出
build/
*/build/
*.apk
*.aab

# Keystore（請勿提交）
*.jks
*.keystore
keystore.properties

# IDE
.idea/
*.iml
```

## 檢查清單

完成所有步驟後，使用此檢查清單確認：

- [ ] 所有解析度的圖示檔案都已準備並放置於正確位置
- [ ] `Config.kt` 介面中新增了新的屬性定義
- [ ] `ConfigImpl.kt` 中實作了新的屬性並更新了 `AAPSCLIENT` 定義
- [ ] `IconsProviderImplementation.kt` 中新增了圖示映射
- [ ] `app/build.gradle.kts` 中新增了完整的 flavor 配置
- [ ] `android-module-dependencies.gradle.kts` 中同步新增了 flavors
- [ ] `wear/build.gradle.kts` 中新增了對應配置（如果使用）
- [ ] `google-services.json` 中為每個新 variant 新增了 client 配置
- [ ] 執行 `./gradlew clean` 清理專案
- [ ] 成功建置至少一個新 variant
- [ ] 已簽名的 APK 可成功安裝到裝置
- [ ] 在裝置上可看到新的應用程式圖示和名稱
- [ ] 應用程式可正常啟動和運行

## 參考資訊

### 相關檔案位置快速參考

```
AndroidAPS/
├── app/
│   ├── build.gradle.kts                           # App 模組 flavors 配置
│   ├── google-services.json                       # Firebase 配置
│   └── src/main/kotlin/app/aaps/implementations/
│       └── ConfigImpl.kt                          # Config 實作
│
├── buildSrc/src/main/kotlin/
│   └── android-module-dependencies.gradle.kts     # Library 模組 flavors
│
├── core/
│   ├── interfaces/src/main/kotlin/app/aaps/core/interfaces/configuration/
│   │   └── Config.kt                              # Config 介面
│   └── ui/src/main/res/
│       ├── mipmap-hdpi/                           # 圖示檔案
│       ├── mipmap-mdpi/
│       ├── mipmap-xhdpi/
│       ├── mipmap-xxhdpi/
│       └── mipmap-xxxhdpi/
│
├── implementation/src/main/kotlin/app/aaps/implementation/resources/
│   └── IconsProviderImplementation.kt             # 圖示提供者
│
└── wear/
    └── build.gradle.kts                           # Wear 模組 flavors
```

### 命名慣例

| 項目 | 格式 | 範例 |
|------|------|------|
| Flavor 名稱 | `aapsclientX` | `aapsclient3` |
| Application ID | `info.nightscout.aapsclientX` | `info.nightscout.aapsclient3` |
| App 名稱 | `AAPSClientX` | `AAPSClient3` |
| 版本後綴 | `-aapsclientX` | `-aapsclient3` |
| 圖示檔名 | `ic_[顏色]owl.png` | `ic_greenowl.png` |
| Config 屬性 | `AAPSCLIENTX` | `AAPSCLIENT3` |

### 顏色參考

已使用的顏色：
- aapsclient: 黃色 (yellowowl)
- aapsclient2: 藍色 (blueowl)
- aapsclient3: 綠色 (greenowl)
- aapsclient4: 紫色 (purpleowl)
- aapsclient5: 深藍色 (darkbuleowl)
- aapsclient6: 灰色 (grayowl)

建議未來使用的顏色：
- 橙色 (orangeowl)
- 紅色 (redowl)
- 粉色 (pinkowl)
- 青色 (cyanowl)
- 棕色 (brownowl)
- 淺藍色 (lightblueowl)

## 版本歷史

- **v1.0** (2025-10-21): 初始版本，支援 aapsclient3-6
  - 新增 4 個新的 AAPSClient variants
  - 建立完整的擴充規格文件

## 授權

本文件遵循 AndroidAPS 專案相同的授權協議。

## 維護者

如有問題或建議，請聯繫 AndroidAPS 開發團隊或在專案 repository 中提出 issue。

---

**最後更新：** 2025年10月21日
**文件版本：** 1.0
