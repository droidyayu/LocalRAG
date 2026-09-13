plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "com.ayushig.localrag"
version = "0.1.0"

android {
    namespace = "com.ayushig.localrag.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Resolved by dependency substitution against the included localrag-tooling build. api, not
    // implementation: Passage and the bundle types are part of this modules public surface.
    api("com.ayushig.localrag:localrag-core:0.1.0")
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "localrag-android"
            }
        }
    }
}
