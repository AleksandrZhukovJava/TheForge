import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

// Single source of truth for the app version — used for the installer AND baked into a resource
// the in-app updater reads (AppVersion.CURRENT). Bump this one line to cut a new release.
val appVersion = "1.0.1"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(project(":forge-sdk"))
    implementation(project(":forge-brain"))
    implementation(project(":forge-executors"))
    implementation(project(":plugins:integration-jira"))
    implementation(project(":plugins:integration-gitlab"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
}

// Write the version into a generated resource so runtime code has one authoritative value.
val generatedVersionDir = layout.buildDirectory.dir("generated/version")
val generateVersion by tasks.registering {
    val outDir = generatedVersionDir
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        dir.resolve("forge-version.txt").writeText(version)
    }
}
sourceSets["main"].resources.srcDir(generatedVersionDir)
tasks.named("processResources") { dependsOn(generateVersion) }

compose.desktop {
    application {
        mainClass = "com.forge.workshop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "TheForge"
            packageVersion = appVersion
        }
    }
}
