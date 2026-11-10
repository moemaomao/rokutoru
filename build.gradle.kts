import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

group = "com.rokutoru"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Serialization (untuk config & glossary)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // HTTP client (untuk Vision LLM API)
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-cio:3.1.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
}

compose.desktop {
    application {
        mainClass = "com.rokutoru.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "Rokutoru"
            packageVersion = "1.0.0"
            description = "AI-powered Manga Translation Tool"
            copyright = "© 2026 Rokutoru"
            vendor = "Rokutoru"

            windows {
                menuGroup = "Rokutoru"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            macOS {
                bundleID = "com.rokutoru.app"
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}