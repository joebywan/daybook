plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---- release signing --------------------------------------------------------------------------
//
// These locals are deliberately NOT called keyAlias / keyPassword. Those are SigningConfig's own
// property names, so inside the signingConfigs block a local of that name is shadowed by the
// config's own property and the config ends up reading itself instead of the environment.
val ksPath = System.getenv("DAYBOOK_KEYSTORE").orEmpty()
val ksPassword = System.getenv("DAYBOOK_KEYSTORE_PASSWORD").orEmpty()
val ksAlias = System.getenv("DAYBOOK_KEY_ALIAS").orEmpty()
val ksKeyPassword = System.getenv("DAYBOOK_KEY_PASSWORD").orEmpty()

val haveReleaseKey = ksPath.isNotBlank() &&
    ksPassword.isNotBlank() &&
    ksAlias.isNotBlank() &&
    ksKeyPassword.isNotBlank() &&
    File(ksPath).exists()

// A successful build is NOT proof the APK was signed: if any one of those is missing, AGP quietly
// produces app-release-unsigned.apk and exits zero. Release builds pass -PrequireSigning so that
// situation fails the build instead of shipping an unsignable artifact.
val requireSigning = (findProperty("requireSigning") as String?).toBoolean()
if (requireSigning && !haveReleaseKey) {
    throw GradleException(
        "-PrequireSigning was set but the release key is not available. " +
            "Need DAYBOOK_KEYSTORE (exists: ${ksPath.isNotBlank() && File(ksPath).exists()}), " +
            "DAYBOOK_KEYSTORE_PASSWORD (set: ${ksPassword.isNotBlank()}), " +
            "DAYBOOK_KEY_ALIAS (set: ${ksAlias.isNotBlank()}), " +
            "DAYBOOK_KEY_PASSWORD (set: ${ksKeyPassword.isNotBlank()})."
    )
}

// CI supplies these so every published build carries a rising versionCode; local builds get 1.
val buildVersionCode = (System.getenv("DAYBOOK_VERSION_CODE") ?: "1").toInt()
val buildVersionName = System.getenv("DAYBOOK_VERSION_NAME") ?: "0.1.0"

android {
    namespace = "com.joebywan.daybook"
    compileSdk = 36

    defaultConfig {
        // Locked: changing this after the first release makes every installed copy un-updatable.
        applicationId = "com.joebywan.daybook"
        minSdk = 26
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = buildVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (haveReleaseKey) {
            create("release") {
                storeFile = File(ksPath)
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
                // v1 as well as v2/v3 so the certificate stays readable by every verifier.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Explicit rather than relying on the default: a debuggable APK is what Play Protect
            // scrutinises hardest, and declining its scan is what blocks an in-place update.
            isDebuggable = false

            // Deliberately off. Saved games are serialised by class name — kotlinx.serialization
            // writes the state class into the JSON — so shrinking would buy about a megabyte in
            // exchange for a class of failure that only ever shows up in the shipped build, on
            // someone's half-finished board.
            isMinifyEnabled = false
            isShrinkResources = false

            signingConfig = if (haveReleaseKey) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            // Without this the compiler stays silent about unused declarations and values that
            // are assigned and never read -- verified by probing with a deliberately unused
            // function, which produced no output at all. "Builds with no warnings" was therefore
            // a much weaker claim than it sounded.
            extraWarnings.set(true)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
