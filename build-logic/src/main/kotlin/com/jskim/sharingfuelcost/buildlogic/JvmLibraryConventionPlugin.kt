package com.jskim.sharingfuelcost.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }

        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(JAVA_VERSION.toString()))
        }
    }
}
