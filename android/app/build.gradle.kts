import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

val toolboxDebugApiBaseUrl = providers.gradleProperty("toolboxDebugApiBaseUrl")
    .getOrElse("http://10.0.2.2:8081/api/v1")
val toolboxDebugApiUri = URI(toolboxDebugApiBaseUrl)
require(toolboxDebugApiUri.scheme == "http" && toolboxDebugApiUri.host in setOf("localhost", "127.0.0.1", "10.0.2.2") &&
    toolboxDebugApiUri.port in 1..65535 && toolboxDebugApiUri.path.trimEnd('/') == "/api/v1" &&
    toolboxDebugApiUri.userInfo == null && toolboxDebugApiUri.query == null && toolboxDebugApiUri.fragment == null) {
    "Debug API URL must use an allowed loopback host and /api/v1 path"
}

android {
    namespace = "com.cangshuo.toolbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cangshuo.toolbox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Decision 019: official-site base URL for WEB tools; debug overrides it below.
        buildConfigField("String", "WEB_TOOL_BASE_URL", "\"https://tool.zhzgo.cn\"")
        buildConfigField("String", "API_BASE_URL", "\"https://toolapi.zhzgo.cn/api/v1\"")
    }

    androidResources {
        localeFilters += listOf("zh", "en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "WEB_TOOL_BASE_URL", "\"http://localhost:8088\"")
            // Physical devices use -PtoolboxDebugApiBaseUrl=http://127.0.0.1:8081/api/v1 with adb reverse.
            buildConfigField("String", "API_BASE_URL", "\"${toolboxDebugApiUri.toASCIIString()}\"")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.datastore)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.zxing.core)
    implementation(libs.re2j)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
