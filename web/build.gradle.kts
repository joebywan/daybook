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
)

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
        val wasmJsMain by getting {
            // The patterns apply to web/'s own source directory as well as app/'s, so a web file
            // must never share a path with one of androidOnly.
            kotlin.srcDir(appSources)
            kotlin.exclude(androidOnly)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                // The shell's icons are the same Material ones Android uses. Kotlin/Wasm drops
                // every icon nothing references, so the whole set costs only the few in use.
                implementation(compose.materialIconsExtended)
                // The bundled fonts; see platformTypography in platform/WebPlatform.kt.
                implementation(compose.components.resources)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.joebywan.daybook.web.resources"
    generateResClass = always
}
