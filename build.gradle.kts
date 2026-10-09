plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}
repositories { mavenCentral() }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
kotlin { jvmToolchain(21) }
application { mainClass.set("MainKt") }
tasks.test { useJUnitPlatform() }

// Bundle this small application's runtime libraries into one executable JAR.
val standaloneJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds a standalone executable JAR with all runtime dependencies."
    archiveClassifier.set("standalone")
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    // Dependency signatures and module descriptors do not describe the combined JAR.
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/INDEX.LIST")
    exclude("module-info.class", "META-INF/versions/**/module-info.class")
}
tasks.assemble { dependsOn(standaloneJar) }
