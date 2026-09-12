// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9.3.2 ships Kotlin Gradle Plugin 2.2.10 as its built-in Kotlin. LiteRT-LM 0.17.0 is compiled
// with Kotlin metadata version 2.4.0, which a 2.2.10 compiler refuses to read. Overriding the KGP
// classpath here is the documented way to raise the built-in Kotlin version for the whole build.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
