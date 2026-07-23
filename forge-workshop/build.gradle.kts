import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

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

compose.desktop {
    application {
        mainClass = "com.forge.workshop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "TheForge"
            packageVersion = "1.0.0"
        }
    }
}
