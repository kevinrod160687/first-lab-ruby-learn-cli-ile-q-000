import org.cyclonedx.gradle.CyclonedxDirectTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    id("org.cyclonedx.bom") version "3.5.1"
}

// The aggregate SBOM should describe what ships, not the Android Gradle Plugin
// or lint classpaths. Those pull a build-only Bouncy Castle that is not in the APK.
subprojects {
    tasks.withType<CyclonedxDirectTask>().configureEach {
        includeConfigs.set(
            listOf(
                "runtimeClasspath",
                "debugRuntimeClasspath",
                "releaseRuntimeClasspath",
            ),
        )
    }
}

// AGP 9 ships Kotlin 2.2.10. Pin the newer compiler and KSP on the buildscript
// classpath so every module, including built-in Kotlin, uses the same version.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}
