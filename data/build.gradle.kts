plugins {
    alias(libs.plugins.sharingfuelcost.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jskim.sharingfuelcost.data"
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    testImplementation(libs.junit)
}
