plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("kotlin-parcelize")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.configuration"
    
    // Control test output verbosity with: -PshowTestOutput=true
    val showTestOutput = project.findProperty("showTestOutput")?.toString()?.toBoolean() ?: false
    
    testOptions {
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed")
                if (showTestOutput) {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
            }
        }
    }
    
    // ==================== Google Drive Integration Test Configuration ====================
    //
    // GoogleDriveManagerIntegrationTest requires a valid Google Drive refresh token.
    // The test will automatically read from environment variables or system properties.
    // Without a token, integration tests will be skipped (unit tests will still run).
    //
    // === How to obtain a refresh token ===
    // 1. Download aaps-ci-preparation.html (v1.1.3 or later) from:
    //    https://github.com/nightscout/aaps-ci-preparation/releases
    // 2. Open the downloaded HTML file in a browser and select "Custom" mode
    // 3. Enter Client ID: 705061051276-3ied5cqa3kqhb0hpr7p0rggoffhq46ef.apps.googleusercontent.com
    // 4. Click "Start Auth" and complete the OAuth flow
    // 5. The page will show a Base64 encoded string (format: client_id|refresh_token)
    // 6. Decode the Base64 string: echo "YOUR_BASE64_STRING" | base64 -d
    // 7. Extract the refresh token (the part after the pipe "|" character)
    //
    // === How to run tests with the token ===
    // Option 1 - Environment variable:
    //   export GOOGLE_DRIVE_REFRESH_TOKEN="1//0eXXX..."
    //   ./gradlew :plugins:configuration:testFullReleaseUnitTest
    //
    // Option 2 - Inline environment variable:
    //   GOOGLE_DRIVE_REFRESH_TOKEN="1//0eXXX..." ./gradlew :plugins:configuration:testFullReleaseUnitTest
    //
    // Option 3 - System property:
    //   ./gradlew :plugins:configuration:testFullReleaseUnitTest -DGOOGLE_DRIVE_REFRESH_TOKEN="1//0eXXX..."
    //
    // ======================================================================================
}


dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":shared:impl"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":implementation"))
    testImplementation(libs.com.google.truth)

    //WorkManager
    api(libs.androidx.work.runtime)
    // Maintenance
    api(libs.androidx.gridlayout)
    
    // HTTP client for Google Drive API
    implementation(libs.com.squareup.okhttp3.okhttp)

    // Chrome Custom Tabs for OAuth flow
    api(libs.androidx.browser)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}