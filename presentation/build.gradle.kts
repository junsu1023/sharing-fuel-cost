plugins {
    alias(libs.plugins.sharingfuelcost.android.library)
    alias(libs.plugins.sharingfuelcost.android.compose)
}

android {
    namespace = "com.jskim.sharingfuelcost.presentation"
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    testImplementation(libs.junit)
}
