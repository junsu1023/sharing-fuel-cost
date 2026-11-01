plugins {
    alias(libs.plugins.sharingfuelcost.android.library)
    alias(libs.plugins.sharingfuelcost.android.compose)
}

android {
    namespace = "com.jskim.sharingfuelcost.presentation"
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
