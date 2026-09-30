import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The puzzle code is compiled from app/'s own source files, not copied: one Kings, one seed hash.
// Each file listed here must stay free of android.* and java.* — see CLAUDE.md, "Web build".
val appSources = "../app/src/main/java"
val sharedFromApp = listOf(
    "com/joebywan/daybook/core/Difficulty.kt",
    "com/joebywan/daybook/core/JvmHashOrder.kt",
    "com/joebywan/daybook/core/PuzzleType.kt",
    "com/joebywan/daybook/core/Rng.kt",
    "com/joebywan/daybook/core/SeedHash.kt",
    "com/joebywan/daybook/core/Teaching.kt",
    "com/joebywan/daybook/puzzles/PuzzleState.kt",
    "com/joebywan/daybook/puzzles/Kings.kt",
    "com/joebywan/daybook/puzzles/KingsTeacher.kt",
    "com/joebywan/daybook/puzzles/Lits.kt",
    "com/joebywan/daybook/puzzles/Mosaic.kt",
    "com/joebywan/daybook/puzzles/Atoms.kt",
    "com/joebywan/daybook/puzzles/Shikaku.kt",
    "com/joebywan/daybook/puzzles/Snap.kt",
    "com/joebywan/daybook/puzzles/Sudoku.kt",
    "com/joebywan/daybook/ui/theme/Palette.kt",
)

// The app shell — navigation, screens, theme, saves — compiled from app/ as well. Where these files
// need the platform (storage, dates, back button) they call platform/, which each build supplies:
// app/'s AndroidPlatform.kt there, and web/'s WebPlatform.kt here.
val shellFromApp = listOf(
    "com/joebywan/daybook/core/DailySeed.kt",
    "com/joebywan/daybook/data/KeyValueStore.kt",
    "com/joebywan/daybook/data/ProgressStore.kt",
    "com/joebywan/daybook/ui/DaybookApp.kt",
    "com/joebywan/daybook/ui/archive/ArchiveScreen.kt",
    "com/joebywan/daybook/ui/home/HomeScreen.kt",
    "com/joebywan/daybook/ui/home/LaunchOptions.kt",
    "com/joebywan/daybook/ui/play/PlayScreen.kt",
    "com/joebywan/daybook/ui/stats/StatsScreen.kt",
    "com/joebywan/daybook/ui/teach/Hints.kt",
    "com/joebywan/daybook/ui/theme/Theme.kt",
    "com/joebywan/daybook/ui/tutorial/TutorialRunner.kt",
)

// web/'s own files outside its `web` package. Named rather than globbed, because the include
// patterns apply to app/'s source directory too, and app/ has files in these packages.
val webOwnFiles = listOf(
    "com/joebywan/daybook/platform/WebPlatform.kt",
    // TEMPORARY until every puzzle is in sharedFromApp; see the file.
    "com/joebywan/daybook/core/WebPuzzleRegistry.kt",
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
    }

    sourceSets {
        val wasmJsMain by getting {
            kotlin.srcDir(appSources)
            kotlin.include(sharedFromApp)
            kotlin.include(shellFromApp)
            kotlin.include(webOwnFiles)
            kotlin.include("com/joebywan/daybook/web/**")
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
            }
        }
    }
}

