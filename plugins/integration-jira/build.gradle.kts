plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

val ktor = "2.3.12"

dependencies {
    implementation(project(":forge-sdk"))
    implementation(project(":forge-executors"))
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-cio:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:$ktor")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
