plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// The web module's toolchain downloads come from repositories declared in settings.gradle.kts;
// left to itself the Kotlin plugin would add them per project, which the settings forbid.
// set(null) alone is not enough: it falls back to the plugin's convention, which is the URL.
fun Property<String>.noDownload() {
    convention(null as String?)
    set(null as String?)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().downloadBaseUrl.noDownload()
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().downloadBaseUrl.noDownload()
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec>().downloadBaseUrl.noDownload()
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().downloadBaseUrl.noDownload()
    }
}
