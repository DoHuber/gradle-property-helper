import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}
kotlin {
    // Run the Kotlin compiler on the same build JDK as the desktop, but emit
    // Java 25 bytecode for IDEA 2026.2. Its bundled JBR runs the IDE and tests.
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
repositories {
    intellijPlatform { defaultRepositories() }
}
val ideaVersion = providers.gradleProperty("ideaVersion").orElse("2026.2.3")
val ideaHome = providers.gradleProperty("ideaHome")
dependencies {
    implementation(project(":core"))
    intellijPlatform {
        if (ideaHome.isPresent) local(ideaHome) else intellijIdea(ideaVersion)
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}
// IDEA provides its own Kotlin standard library; do not package another copy.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}
intellijPlatform {
    // Settings live in a tool-window dialog, not the IDE's searchable Settings tree.
    buildSearchableOptions = false
    // All UI is programmatic Kotlin: no GUI Designer forms or Java null-check instrumentation.
    instrumentCode = false
    pluginConfiguration {
        id = "dev.dohuber.gradle-property-helper"
        name = "Gradle Property Helper"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "262"
            // Limit installation to the SDK release series actually tested here.
            untilBuild = "262.*"
        }
    }
}

tasks.named<Zip>("buildPlugin") {
    archiveBaseName.set("gradle-property-helper")
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

// These tests use platform documents/UI only. The bundled Ultimate startup
// extension fails to initialize in the platform test fixture.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareTestSandbox") {
    disabledPlugins.add("com.intellij.modules.ultimate")
}
