# 中國版微泰廣播接收驗證

本分支以 `dev` 的 `5d2852b72f14749daf3379cb3ffc2dc620465f11` 為基準，
加入中國版「微泰动态」的 Android 廣播名稱辨識。
目前為**接收診斷功能，尚未實作血糖匯入**。

## 已確認的名稱

| 項目 | 名稱 |
| --- | --- |
| 中國版 App 套件 | `com.microtech.aidexx` |
| 中國版血糖廣播 action | `com.microtech.aidexx.broadcast.action.CGM_DATA` |
| 原有國際版 Aidex action | `com.microtechmd.cgms.aidex.action.BgEstimate` |

名稱來自 `aidex-x-cn-android-1.15.1.apk` 的靜態分析。
APK SHA-256：`aec574e1c6e8ba230ff5429973fd458454bb68ee8b09f36381ebda293e0d85dc`。
App 已加固；可讀的 `BroadcastSender.isEnable()` 固定回傳 `true`，
但尚未確認實際發送時機、收件套件限制與發送端權限條件。
APK 中另有 `com.microtech.aidexx.broadcast.CGM_DATA` 字串；
其用途尚未確認，因此沒有把它當成另一個 action。

## 本分支的行為

- 在 Manifest 宣告中國版 action，並於 `Intents` 加入 `AIDEX_CN_CGM_DATA`。
- `DataReceiver` 收到該 action 時，以 `BGSOURCE` 記錄收到事件。
- 除錯日誌會嘗試記錄 extras；若廠商自訂類別無法解讀，記錄例外類型並結束處理。
- 中國版事件不會建立血糖匯入工作，也不會送入現有的 `AidexWorker`。
- 原有國際版 action 與解析流程保持原樣。血糖來源選單不宣稱已支援中國版匯入。

## 實機確認方式

1. 編譯此分支，在同一支 Android 手機上安裝 AAPS 與中國版微泰 App。
2. 啟用 AAPS 的 `BGSOURCE` 除錯日誌。這個接收診斷入口不要求切換目前的血糖來源。
3. 保持 AAPS 開啟，等微泰出現一筆新的實際血糖值，記下微泰顯示的量測時間、數值與單位。
4. 在 AAPS 日誌搜尋 `Received MicroTech China CGM_DATA broadcast`，核對收到事件的時間。
5. 搜尋同一時間的 `MicroTech China CGM_DATA extras:`，取得資料欄位與內容。
   接著可分別觀察背景執行、重新連線與歷史資料補傳時的行為。

日誌中的血糖值與感測器識別碼可能屬於個人資料；分享時僅保留所需片段並遮蔽識別碼。
看到接收日誌只代表 action 已到達，並不代表血糖已匯入。
未看到日誌也不能單憑這點認定微泰沒有發送；仍需檢查 Android 背景廣播限制、
發送端指定的接收套件與權限條件。

## 完成血糖匯入前仍需確認

- extras 的 key、資料型別及是否使用 JSON、Bundle 或廠商自訂物件。
- 血糖值的單位與縮放倍率。
- 量測時間的格式、秒／毫秒、時區與歷史資料時間。
- 感測器狀態、暖機、錯誤值、趨勢與重複資料處理方式。

確認後再新增中國版解析與有效性檢查；不要僅將新 action 當成國際版 action 的別名。

## 驗證狀態

已檢查 XML 可解析性、action 定義一致性，以及現有接收流程的差異。
已新增接收測試，涵蓋中國版事件不匯入、無 extras 與不可解讀 extras。
編輯環境缺少 Android SDK 與 JDK 編譯工具，尚未執行 Gradle 測試或 APK 編譯。
也尚未完成實機廣播測試。
