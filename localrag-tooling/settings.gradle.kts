// Included builds run in isolation: no pluginManagement, dependencyResolutionManagement or
// buildscript configuration from the root build reaches this file.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
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
        // The root catalog is not shared with an included build, so load it explicitly.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "localrag-tooling"

include(":localrag-core")
