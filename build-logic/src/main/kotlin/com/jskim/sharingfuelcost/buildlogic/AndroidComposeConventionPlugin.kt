package com.jskim.sharingfuelcost.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.getByType<CommonExtension>().buildFeatures.compose = true

        dependencies {
            val composeBom = platform(libs.findLibrary("androidx-compose-bom").get())
            add("implementation", composeBom)
            add("androidTestImplementation", composeBom)
        }
    }
}
