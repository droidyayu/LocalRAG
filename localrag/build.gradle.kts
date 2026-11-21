plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
}

group = "com.ayushig.localrag"
version = "0.1.0"

kotlin {
    androidLibrary {
        namespace = "com.ayushig.localrag.android"
        compileSdk = 37
        minSdk = 26

        // android.util.Log is called from AgentRunner; without this every unit test that reaches
        // it throws "not mocked". Replaced by a logger seam when the SDK code moves to commonMain.
        withHostTest {
            isReturnDefaultValues = true
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Resolved by dependency substitution against the included localrag-tooling build.
            // api, not implementation: Passage and the bundle types are part of the public surface.
            api("com.ayushig.localrag:localrag-core:0.1.0")
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.litertlm.android)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
