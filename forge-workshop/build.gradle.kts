import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
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
