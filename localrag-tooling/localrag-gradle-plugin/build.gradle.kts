plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.ayushig.localrag"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        // Java 17 is the floor for a plugin that must load in any Gradle 9 daemon.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Gradle 9 embeds Kotlin 2.3.20 with a DSL language level of 2.2. Staying at 2.2 keeps the
        // plugin loadable by older Gradle 9 daemons that ship an older stdlib.
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    implementation(project(":localrag-core"))
    compileOnly(libs.android.gradle.api)
}

gradlePlugin {
    plugins {
        create("localrag") {
            id = "com.ayushig.localrag"
            displayName = "LocalRAG bundle generator"
            description = "Builds an on-device retrieval bundle from Markdown documentation"
            implementationClass = "com.ayushig.localrag.gradle.LocalRagPlugin"
        }
    }
}

// java-gradle-plugin with maven-publish already creates the pluginMaven publication and the
// plugin marker. Declaring publications here would suppress both.
