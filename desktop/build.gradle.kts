plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
kotlin { jvmToolchain(21) }
application {
    mainClass.set("MainKt")
    applicationName = "gradle-property-helper"
}
tasks.test { useJUnitPlatform() }
tasks.named<Sync>("installDist") {
    destinationDir = rootProject.layout.buildDirectory.dir("install/gradle-property-helper").get().asFile
}
val standaloneJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the standalone desktop JAR with all runtime dependencies."
    archiveFileName.set("gradle-property-helper-standalone.jar")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/INDEX.LIST")
    exclude("module-info.class", "META-INF/versions/**/module-info.class")
}
tasks.assemble { dependsOn(standaloneJar) }
tasks.register<Copy>("installStandaloneJar") {
    group = "distribution"
    description = "Copies the standalone JAR to /home/dominik/Tools (or -PtoolsDir)."
    from(standaloneJar.flatMap { it.archiveFile })
    into(providers.gradleProperty("toolsDir").orElse("/home/dominik/Tools"))
}
