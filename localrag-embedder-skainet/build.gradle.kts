import java.util.concurrent.Callable

plugins {
    // Versions come from this build's own settings.gradle.kts, pinned to the Kotlin that can
    // consume SKaiNET's native klibs.
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "com.ayushig.localrag"
version = "0.1.0"

kotlin {
    // SKaiNET 0.57.0 publishes Java 21 bytecode, so unlike localrag-core this module cannot offer
    // a Java 11 variant; the sidecar wrapper checks the JVM it finds for the same reason.
    jvmToolchain(21)

    jvm()
    // The same embedder that indexes the corpus at build time compiles for the phone. iOS first:
    // that is where a Kotlin embedding engine exists today and LiteRT-LM does not.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.skainet.bom))
            implementation(libs.skainet.lang.core)
            implementation(libs.skainet.backend.cpu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

/**
 * One self-contained jar for the sidecar wrapper in tools/skainet-embed. The wrapper locates it
 * relative to itself, so no absolute path enters the embed task's cache key.
 */
val sidecarJar = tasks.register<Jar>("sidecarJar") {
    group = "build"
    description = "Self-contained sidecar jar speaking the LocalRAG embedding protocol on stdin/stdout"
    archiveClassifier.set("sidecar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to "com.ayushig.localrag.embedder.skainet.SidecarMainKt") }

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    val runtimeJars: org.gradle.api.file.FileCollection =
        requireNotNull(jvmCompilation.runtimeDependencyFiles)
    from(jvmCompilation.output.allOutputs)
    dependsOn(jvmCompilation.compileTaskProvider)
    from(Callable { runtimeJars.files.filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class", "module-info.class")
}
