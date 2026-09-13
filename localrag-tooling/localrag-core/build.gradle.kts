plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
    `maven-publish`
}

group = "com.ayushig.localrag"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

// Compiled on JDK 17 but emitting Java 11 bytecode: the Android consumers declare Java 11
// compileOptions, and a variant advertising 17 would fail their dependency resolution.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Gradle 9 embeds Kotlin 2.3.20 but its DSL language level is 2.2. Staying at 2.2 keeps
        // this library loadable by the Gradle plugin that shares it.
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

/**
 * Fails if anything Android or LiteRT reaches this module.
 *
 * The core is shared by the Gradle plugin and the Android runtime, and that sharing is the only
 * thing guaranteeing an index built on CI matches queries typed on a phone. A platform dependency
 * here would quietly end that, so the rule is enforced rather than documented.
 */
val forbiddenDependencyPrefixes = listOf(
    "com.android",
    "androidx",
    "com.google.ai.edge",
    "org.robolectric",
)

tasks.register("verifyNoPlatformDependencies") {
    group = "verification"
    description = "Fails if localrag-core gains an Android or LiteRT dependency"

    val runtimeClasspath = configurations.named("runtimeClasspath")
    val resolved = runtimeClasspath.map { configuration ->
        configuration.incoming.resolutionResult.allDependencies
            .map { it.requested.displayName }
            .sorted()
    }
    val forbidden = forbiddenDependencyPrefixes

    doLast {
        val offenders = resolved.get().filter { dependency ->
            forbidden.any { dependency.startsWith(it) }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "localrag-core must stay free of Android and LiteRT, but found:\n" +
                    offenders.joinToString("\n") { "  " + it },
            )
        }
        logger.lifecycle("localrag-core: ${resolved.get().size} dependencies, none from a platform")
    }
}
