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
    "com/joebywan/daybook/core/PuzzleType.kt",
    "com/joebywan/daybook/core/Rng.kt",
    "com/joebywan/daybook/core/SeedHash.kt",
    "com/joebywan/daybook/puzzles/PuzzleState.kt",
    "com/joebywan/daybook/puzzles/Kings.kt",
    "com/joebywan/daybook/puzzles/Shikaku.kt",
    "com/joebywan/daybook/puzzles/Snap.kt",
    "com/joebywan/daybook/puzzles/Sudoku.kt",
    "com/joebywan/daybook/ui/theme/Palette.kt",
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
            kotlin.include("com/joebywan/daybook/web/**")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.serialization.core)
            }
        }
    }
}

