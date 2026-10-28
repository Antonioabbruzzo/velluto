import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Stesso numero di build della versione Fire TV: le due versioni escono sempre insieme.
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
val updateRepo = System.getenv("UPDATE_REPO") ?: ""

val generateBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildinfo")
    val n = buildNumber
    val repo = updateRepo
    inputs.property("buildNumber", n)
    inputs.property("updateRepo", repo)
    outputs.dir(outDir)
    doLast {
        val f = outDir.get().file("com/velluto/tv/platform/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            """
            |package com.velluto.tv.platform
            |
            |internal object BuildInfo {
            |    const val VERSION_CODE = $n
            |    const val VERSION_NAME = "1.0.$n"
            |    const val UPDATE_REPO = "$repo"
            |}
            |""".trimMargin()
        )
    }
}

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDir("../shared/src")
    // Caratteri in comune con la versione Fire TV, letti come risorse "font/....ttf".
    sourceSets["main"].resources.srcDir("../shared/res")
    sourceSets["main"].kotlin.srcDir(generateBuildInfo)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.json:json:20240303")

    // Player video: usa il VLC installato sul PC
    implementation("uk.co.caprica:vlcj:4.8.3")
}

compose.desktop {
    application {
        mainClass = "com.velluto.tv.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Velluto"
            packageVersion = "1.0.$buildNumber"
            description = "Velluto"
            vendor = "Velluto"
            includeAllModules = true
            windows {
                menu = true
                menuGroup = "Velluto"
                shortcut = true
                perUserInstall = true
                dirChooser = false
                // Non cambiare: permette agli installer nuovi di aggiornare quello vecchio.
                upgradeUuid = "8b7f3c2e-5a1d-4e9b-9c6f-2d4a7e1b3f90"
            }
        }
    }
}
