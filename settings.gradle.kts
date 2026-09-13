pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LocalRAG"

// A Gradle plugin cannot be applied by a sibling project in the same build, so the plugin and the
// core it shares with the runtime live in an included build. The top-level form gives both plugin
// resolution and dependency substitution for com.ayushig.localrag:localrag-core.
includeBuild("localrag-tooling")

include(":app")
 