import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    kotlin("plugin.compose") version "2.3.20"
    id("org.jetbrains.compose") version "1.12.2"
}

group = "com.drxwnify"
version = "0.1.0"

// Plain-Kotlin API clients (YouTube Music, Spotify, Yandex, Last.fm, lyrics), forked from the
// phone app and maintained here on their own.
val sharedModules = listOf("innertube", "spotify", "yandex", "lastfm", "lrclib", "kugou", "betterlyrics")

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        sharedModules.forEach { kotlin.srcDir("shared/$it") }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xwhen-guards")
        optIn.addAll(
            "kotlinx.serialization.ExperimentalSerializationApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

val bytedeco = "1.5.14"
val ffmpeg = "8.1.2-$bytedeco"

dependencies {
    implementation(compose.desktop.currentOs)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("dev.chrisbanes.haze:haze:2.0.1")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.1")

    // What the shared modules need on the JVM.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    implementation(libs.newpipeextractor)
    implementation(libs.json)

    // System-wide hotkeys (RegisterHotKey).
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Reads the phone's song.db out of its .backup.
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")

    // Decoding: FFmpeg plays everything the phone plays (Opus/AAC/FLAC, HLS with AES-128).
    implementation("org.bytedeco:javacv:$bytedeco") {
        listOf(
            "opencv", "flycapture", "libdc1394", "libfreenect", "libfreenect2", "librealsense",
            "librealsense2", "videoinput", "artoolkitplus", "flandmark", "leptonica", "tesseract", "openblas",
        ).forEach { exclude(group = "org.bytedeco", module = it) }
    }
    implementation("org.bytedeco:javacpp:$bytedeco")
    implementation("org.bytedeco:javacpp:$bytedeco:windows-x86_64")
    implementation("org.bytedeco:ffmpeg:$ffmpeg")
    implementation("org.bytedeco:ffmpeg:$ffmpeg:windows-x86_64")
}

compose.desktop {
    application {
        mainClass = "com.drxwnify.desktop.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dsun.java2d.uiScale.enabled=true", "-Xmx1024m")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Drxwnify"
            packageVersion = "0.1.0"
            description = "Drxwnify music player"
            vendor = "Drxwnify"
            modules("java.net.http", "java.sql", "jdk.crypto.ec", "jdk.unsupported")
            windows {
                menuGroup = "Drxwnify"
                upgradeUuid = "6f2b3c1e-6a2d-4d7e-9a41-5c0d2b9e7f13"
                shortcut = true
                dirChooser = true
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}
