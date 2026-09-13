plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("com.ayushig.localrag")
}

// Documentation lives in the app, not the library. The plugin indexes it at build time and packs
// the result into assets/localrag/docs.localrag.
localRag {
    docsDir.set(layout.projectDirectory.dir("src/main/docs"))
    outputAssetDir.set("localrag")
    categories.set(listOf("orders", "account", "funds", "charges", "kyc"))
    screenUriPattern.set("^app://[a-z0-9/-]+$")
    staleAfterDays.set(365)
    maxChunkTokens.set(400)
    contentVersion.set(1)

    embedding {
        // Off by default: a vector-less bundle is a supported runtime state, and turning this on
        // needs the sidecar toolchain described in tools/embed/README.md.
        // Flip with -PlocalRagEmbed=true to exercise the embedding path.
        val embed = providers.gradleProperty("localRagEmbed").orNull == "true"
        enabled.set(embed)
        dimensions.set(256)
        sidecarCommand.set(
            listOf("python3", rootProject.file("tools/embed/hash_embed.py").absolutePath, "--dimensions", "256"),
        )
    }
}

android {
    namespace = "com.ayushig.localrag.demo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ayushig.localrag.demo"
        // java.time is used across the portfolio models; it needs API 26 without desugaring.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // LiteRT-LM ships native libraries for arm64-v8a and x86_64 only, and the spike is
        // measured on physical arm64 hardware.
        ndk {
            abiFilters += "arm64-v8a"
        }

        // Surfaced in the chat top bar so a screenshot of a run always names the exact runtime.
        buildConfigField("String", "LITERTLM_VERSION", "\"${libs.versions.litertlm.get()}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.litertlm.android)
    implementation(project(":localrag-android"))
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}