plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "sharingfuelcost.android.application"
            implementationClass = "com.jskim.sharingfuelcost.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "sharingfuelcost.android.library"
            implementationClass = "com.jskim.sharingfuelcost.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "sharingfuelcost.android.compose"
            implementationClass = "com.jskim.sharingfuelcost.buildlogic.AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "sharingfuelcost.jvm.library"
            implementationClass = "com.jskim.sharingfuelcost.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
