import java.util.Properties

plugins {
    alias(libs.plugins.sharingfuelcost.android.application)
    alias(libs.plugins.sharingfuelcost.android.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase 설정 파일은 git에 넣지 않는다. 없으면 플러그인을 적용하지 않고, 앱은 App Check 토큰 없이 동작한다.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// 지도 SDK의 네이티브 앱 키만 앱에 둔다. REST API 키들은 중계 서버(relay/)의 비밀값에 있다.
val kakaoNativeAppKey: String = localProperties.getProperty("kakao_native_app_key").orEmpty()
val relayBaseUrl: String = localProperties.getProperty("relay_base_url").orEmpty()

// 업로드 키는 저장소 밖에 두고 local.properties에 경로와 비밀번호를 적는다. 없으면 release는 서명하지 않는다.
val releaseStoreFile: String? = localProperties.getProperty("release_store_file")
val releaseStorePassword: String? = localProperties.getProperty("release_store_password")
val releaseKeyAlias: String? = localProperties.getProperty("release_key_alias")
val releaseKeyPassword: String? = localProperties.getProperty("release_key_password")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.jskim.sharingfuelcost"

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.jskim.sharingfuelcost"
        // Play에 올릴 때마다 1씩 올린다. 한 번 올린 번호는 다시 쓸 수 없다.
        versionCode = 2
        versionName = "1.0"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        buildConfigField("String", "RELAY_BASE_URL", "\"$relayBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        // AGP 9.2는 optimization 블록 대신 이 설정으로 R8을 켠다. (9.3부터 새 DSL)
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":presentation"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kakao.map)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}