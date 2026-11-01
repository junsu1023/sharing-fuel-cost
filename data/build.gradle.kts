plugins {
    alias(libs.plugins.sharingfuelcost.android.library)
}

android {
    namespace = "com.jskim.sharingfuelcost.data"
}

dependencies {
    implementation(project(":domain"))
    testImplementation(libs.junit)
}
