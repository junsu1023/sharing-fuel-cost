package com.jskim.sharingfuelcost.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal const val TARGET_SDK = 36

internal val JAVA_VERSION = JavaVersion.VERSION_11

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun CommonExtension.configureAndroid() {
    compileSdk {
        version = release(37)
    }

    defaultConfig.apply {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions.apply {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
}
