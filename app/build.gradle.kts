plugins {
    alias(libs.plugins.android.application)
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
    compileSdk = 37

    defaultConfig {
        // Locked: changing this after the first release makes every installed copy un-updatable.
        applicationId = "com.joebywan.daybook"
        // 25, not 26, so the Galaxy Tab A 8.0 (SM-T355Y, stuck on Android 7.1.1) can install it. java.time
        // (ours and kotlinx-datetime's) is API 26, hence core library desugaring below.
        minSdk = 25
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = buildVersionName
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

            // R8 on: the release APK drops from 13.5 MB to 2.3 MB (measured; all of it code). Saved games
            // are the thing to watch, because kotlinx.serialization writes the state class's name into the
            // JSON. R8 renames classes but leaves the name it writes (a string constant, and the library
            // ships its own R8 rules), and that was checked rather than assumed: with R8 on, 48 saves
            // (every puzzle at every tier) loaded and re-saved byte-identical on an emulator, an in-place
            // update kept a real game, and saves written by the R8 build load in a normal one. Anything
            // that touches a PuzzleState, its serialisation or R8's rules is now re-checked that way on every
            // release (tools/save-check/check.py, run by release.yml before it publishes). The build must also
            // stay reproducible for F-Droid (docs/fdroid).
            isMinifyEnabled = true
            // Drops the libraries' resources nothing references (Play recommends it). Every resource of ours is
            // referenced from code or the manifest, never looked up by name.
            isShrinkResources = true

            signingConfig = if (haveReleaseKey) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    // AGP otherwise appends an encrypted "dependency metadata" block to the APK's signing block, meant
    // for Google Play. F-Droid's scanner rejects any APK with an extra signing block, and nothing here
    // reads it; the same setting applies to the bundle so both artifacts stay alike.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// The suite is a few long generator/teacher classes and many short ones, and one JVM ran them in
// series (~25 min). Forks take whole classes, so nothing is shared but the files under build/reports,
// each written by one class only. The longest class (TargetTeachingTest, ~6 min) sets the floor.
// Capped at 4, CI's core count: uncapped, a 32-core desktop started 32 test JVMs per run, and two sessions
// testing at once left the machine unusable.
tasks.withType<Test>().configureEach {
    maxParallelForks = minOf(4, Runtime.getRuntime().availableProcessors())
}

// AGP 9 compiles Kotlin itself (built-in Kotlin), so there is no kotlin-android plugin; this is the
// same `kotlin` extension it provides.
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
}
