// A standalone build, isolated like localrag-tooling, but for the opposite reason: SKaiNET's
// Kotlin/Native klibs carry ABI 2.4.0, which the repo-wide Kotlin 2.3.20 cannot consume. This
// build pins its own Kotlin so the iOS targets compile without touching the Kotlin version the
// app and the tooling build on.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("multiplatform") version "2.4.20"
        kotlin("plugin.serialization") version "2.4.20"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        // The root catalog is not shared with a standalone build, so load it explicitly.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "localrag-embedder-skainet"
