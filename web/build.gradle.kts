import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The whole app is compiled from app/'s own source files, not copied: one set of puzzles, one shell.
// Everything under app/src/main/java is on the web except the files named here, which need
// Android; each has a web counterpart (platform/WebPlatform.kt and web/'s storage). A new file in
// app/ is therefore on the web by default, and must stay free of android.* and java.* unless it is
// added here — see CLAUDE.md, "Web build".
val appSources = "../app/src/main/java"
val androidOnly = listOf(
    "com/joebywan/daybook/MainActivity.kt",
    "com/joebywan/daybook/data/DataStoreKeyValueStore.kt",
    "com/joebywan/daybook/platform/AndroidPlatform.kt",
    "com/joebywan/daybook/platform/AndroidReminder.kt",
)

// The version Settings shows on the web (`rememberAppVersion`), so a bug report can say which build it came from:
// version.txt, and the commit Actions built (GITHUB_SHA), else "local". The APK's run number is not known to Pages.
val webVersionDir = layout.buildDirectory.dir("generated/webVersion")
val webVersionText = providers.fileContents(rootProject.layout.projectDirectory.file("version.txt")).asText.map { it.trim() }
val webCommit = providers.environmentVariable("GITHUB_SHA").map { it.take(7) }.orElse("local")
val generateWebVersion by tasks.registering {
    val out = webVersionDir
    val version = webVersionText
    val commit = webCommit
    inputs.property("version", version)
    inputs.property("commit", commit)
    outputs.dir(out)
    doLast {
        val file = out.get().file("com/joebywan/daybook/platform/WebVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.joebywan.daybook.platform\n\n" +
                "/** Written by web/build.gradle.kts: version.txt and the commit built. */\n" +
                "const val WEB_VERSION: String = \"${version.get()} (${commit.get()})\"\n",
        )
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("daybook")
        browser {
            commonWebpackConfig {
                outputFileName = "daybook.js"
            }
            testTask { enabled = false }
        }
        binaries.executable()
    }

    compilerOptions {
        extraWarnings.set(true)
        // Kotlin 2.4 marks the whole JS interop surface (js(), JsAny, external) experimental; the
        // web's platform code is built on it, so opt in once here rather than per file.
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    sourceSets {
        getByName("wasmJsMain") {
            // The patterns apply to web/'s own source directory as well as app/'s, so a web file
            // must never share a path with one of androidOnly.
            kotlin.srcDir(appSources)
            kotlin.srcDir(generateWebVersion)
            kotlin.exclude(androidOnly)
            dependencies {
                implementation(libs.jb.compose.material3)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                // The shell's icons are the same Material ones Android uses. Kotlin/Wasm drops
                // every icon nothing references, so the whole set costs only the few in use.
                implementation(libs.jb.compose.material.icons.extended)
                // The bundled fonts; see platformTypography in platform/WebPlatform.kt.
                implementation(libs.jb.compose.resources)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.joebywan.daybook.web.resources"
    generateResClass = always
}
