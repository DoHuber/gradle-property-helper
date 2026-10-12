plugins {
    base
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
}

allprojects {
    group = "dev.dohuber.gradleproperties"
    version = "0.2.0"
    repositories { mavenCentral() }
}

// Keep the existing desktop commands and artifact locations available at the root.
tasks.register("standaloneJar") { dependsOn(":desktop:standaloneJar") }
tasks.register("installStandaloneJar") { dependsOn(":desktop:installStandaloneJar") }
tasks.register("installDist") { dependsOn(":desktop:installDist") }
tasks.register("run") { dependsOn(":desktop:run") }
tasks.register("test") { dependsOn(":core:test", ":desktop:test") }
tasks.named("assemble") { dependsOn(":desktop:assemble") }
tasks.named("check") { dependsOn(":core:test", ":desktop:test") }
tasks.register("buildPlugin") {
    group = "distribution"
    description = "Builds the installable IntelliJ IDEA plugin ZIP."
    dependsOn(":intellij-plugin:buildPlugin")
}
