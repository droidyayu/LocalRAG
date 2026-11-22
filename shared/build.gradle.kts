plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

compose.resources {
    packageOfResClass = "com.ayushig.localrag.demo.resources"
    generateResClass = always
}

kotlin {
    androidLibrary {
        namespace = "com.ayushig.localrag.demo.shared"
        compileSdk = 37
        minSdk = 26
        // Compose Multiplatform resources are packaged as Android assets only when the KMP
        // library plugin processes Android resources; without this the APK has no
        // composeResources and the app crashes on the first drawable.
        androidResources { enable = true }
        withHostTest { isReturnDefaultValues = true }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Swift implements the SDK engine interfaces, so they must be visible in the framework.
            export(project(":localrag"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":localrag"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel.compose.kmp)
            implementation(libs.lifecycle.runtime.compose.kmp)
            implementation(libs.navigation.compose.kmp)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core.viewmodel)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
