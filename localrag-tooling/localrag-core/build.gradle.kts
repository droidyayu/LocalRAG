import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "com.ayushig.localrag"
version = "0.1.0"

kotlin {
    // Compiled on JDK 17 but emitting Java 11 bytecode: the Android consumers declare Java 11
    // compileOptions, and a variant advertising 17 would fail their dependency resolution.
    jvmToolchain(17)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        // Kotlin 2.3 reads this as the Java release for the bytecode and the variant attribute.
        compilations.configureEach {
            compileTaskProvider.configure {
                (this as org.jetbrains.kotlin.gradle.tasks.KotlinCompile)
                    .compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // Gradle 9 embeds Kotlin 2.3.20 but its DSL language level is 2.2. Staying at 2.2 keeps
        // this library loadable by the Gradle plugin that shares it.
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            // Both appear in public signatures (DocumentValidator.today, BundleReader paths).
            api(libs.kotlinx.datetime)
            api(libs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

/**
 * Fails if anything Android or LiteRT reaches this module, on ANY target.
 *
 * The core is shared by the Gradle plugin and the runtimes on both platforms, and that sharing is
 * the only thing guaranteeing an index built on CI matches queries typed on a phone. A platform
 * dependency here would quietly end that, so the rule is enforced rather than documented.
 *
 * It checks every resolvable compile and runtime classpath of every target, and it fails when it
 * finds nothing to check, so a renamed configuration cannot turn the gate into a no-op.
 */
val forbiddenDependencyPrefixes = listOf(
    "com.android",
    "androidx",
    "com.google.ai.edge",
    "org.robolectric",
)

tasks.register("verifyNoPlatformDependencies") {
    group = "verification"
    description = "Fails if localrag-core gains an Android or LiteRT dependency on any target"

    val checkedConfigurations = configurations
        .matching { it.isCanBeResolved && (it.name.endsWith("CompileClasspath") || it.name.endsWith("RuntimeClasspath") || it.name.endsWith("CompileKlibraries")) }
    val resolved = provider {
        checkedConfigurations.associate { configuration ->
            configuration.name to configuration.incoming.resolutionResult.allDependencies
                .map { it.requested.displayName }
                .sorted()
        }
    }
    val forbidden = forbiddenDependencyPrefixes

    doLast {
        val byConfiguration = resolved.get()
        check(byConfiguration.isNotEmpty()) {
            "verifyNoPlatformDependencies found no classpath to inspect; the check would pass vacuously"
        }
        val offenders = byConfiguration.flatMap { (configuration, dependencies) ->
            dependencies.filter { dependency -> forbidden.any { dependency.startsWith(it) } }
                .map { "$configuration: $it" }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "localrag-core must stay free of Android and LiteRT, but found:\n" +
                    offenders.joinToString("\n") { "  " + it },
            )
        }
        logger.lifecycle(
            "localrag-core: ${byConfiguration.size} classpaths checked, none from a platform",
        )
    }
}
