import java.util.Properties

plugins {
    alias(libs.plugins.sharingfuelcost.android.application)
    alias(libs.plugins.sharingfuelcost.android.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val kakaoRestApiKey: String = localProperties.getProperty("kakao_rest_api_key").orEmpty()
val kakaoNativeAppKey: String = localProperties.getProperty("kakao_native_app_key").orEmpty()
val opinetApiKey: String = localProperties.getProperty("opinet_api_key").orEmpty()
val dataGoKrApiKey: String = localProperties.getProperty("data_go_kr_api_key").orEmpty()

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
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestApiKey\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        buildConfigField("String", "OPINET_API_KEY", "\"$opinetApiKey\"")
        buildConfigField("String", "DATA_GO_KR_API_KEY", "\"$dataGoKrApiKey\"")
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
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}