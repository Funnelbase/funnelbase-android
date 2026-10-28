// The demo app: a trading app that consumes :sdk-android exactly as a customer would.
//
// Like :sdk-android this applies no `org.jetbrains.kotlin.android` plugin — AGP 9 has built-in
// Kotlin support and rejects it. The Compose compiler plugin is applied on top, at the Kotlin
// version the build actually uses (see gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Firebase's own plugin reads google-services.json into the build. That file names a real Firebase
// project, is gitignored, and most people cloning this repo do not have one — so the plugin is
// applied only when it is there, and the app builds and runs either way, with push through a
// custom gateway instead. Checked through `providers` rather than `File.exists()` so the
// configuration cache treats the file as an input and notices when it appears.
val googleServicesJson = providers.fileContents(layout.projectDirectory.file("google-services.json")).asText
if (googleServicesJson.isPresent) {
    apply(plugin = "com.google.gms.google-services")
    logger.lifecycle(":demo-android: google-services.json found — Firebase push is wired in.")
} else {
    logger.lifecycle(
        ":demo-android: no google-services.json — the app builds without Firebase. " +
            "Register a token by hand on the Notifications screen, or see docs/demo-app.md.",
    )
}

/**
 * Reads a key from the gitignored `local.properties`.
 *
 * Via `providers.fileContents` rather than `File.inputStream()` so the configuration cache
 * tracks the file as an input. Read it directly and a stale cached value survives every edit
 * until something else invalidates the cache — which, for an ingest key, means silently
 * building against the wrong credentials.
 */
val localProperties: Provider<Map<String, String>> =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text ->
            text.lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                .associate { line ->
                    val index = line.indexOf('=')
                    line.take(index).trim() to line.substring(index + 1).trim()
                }
        }
        .orElse(emptyMap())

fun analyticsSetting(key: String, default: String): String =
    localProperties.get()[key] ?: providers.environmentVariable(key).orNull ?: default

android {
    namespace = "com.funnelbase.demo.trading"
    compileSdk = 36

    defaultConfig {
        // Older than the code package, and kept: a Firebase project and its google-services.json
        // are registered against this id.
        applicationId = "com.analytics.demo.trading"
        // Matches :sdk-android. A demo that ran on older devices than the SDK supports would be
        // demonstrating something the SDK cannot actually do.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the emulator's route to the host machine, where a local ingestion
            // server listens. On a physical device this needs the host's LAN address instead.
            // No key is committed: set ANALYTICS_API_KEY_DEBUG in local.properties (README.md).
            analyticsBuildConfig(
                endpoint = analyticsSetting("ANALYTICS_ENDPOINT_DEBUG", "http://10.0.2.2:8080"),
                apiKey = analyticsSetting("ANALYTICS_API_KEY_DEBUG", ""),
                appId = analyticsSetting("ANALYTICS_APP_ID_DEBUG", ""),
            )
        }
        release {
            isMinifyEnabled = false
            analyticsBuildConfig(
                endpoint = analyticsSetting("ANALYTICS_ENDPOINT_RELEASE", "https://ingest.example.com"),
                apiKey = analyticsSetting("ANALYTICS_API_KEY_RELEASE", ""),
                appId = analyticsSetting("ANALYTICS_APP_ID_RELEASE", ""),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Same reason as :sdk-android — Android framework classes are stubs under unit tests,
        // and returning defaults instead of throwing lets the pure logic (UserId, Money, the
        // data generators) be tested on the JVM without an emulator.
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * The three analytics settings, as BuildConfig fields.
 *
 * `apiKey` and `endpoint` are what the SDK needs. `appId` is not — the ingestion API resolves it
 * from the key and the client has no field for it: the server stamps the app from the key and
 * never takes it from a client. It is carried anyway so the debug footer can show which app the
 * build is pointed at, which is the first thing you want to know when events go missing.
 */
fun com.android.build.api.dsl.ApplicationBuildType.analyticsBuildConfig(
    endpoint: String,
    apiKey: String,
    appId: String,
) {
    buildConfigField("String", "ANALYTICS_ENDPOINT", "\"$endpoint\"")
    buildConfigField("String", "ANALYTICS_API_KEY", "\"$apiKey\"")
    buildConfigField("String", "ANALYTICS_APP_ID", "\"$appId\"")
}

dependencies {
    implementation(project(":sdk-android"))
    // Push through Firebase: one messaging service, and no push code in this app at all. The
    // module resolves without a Firebase project; only the plugin below needs one.
    implementation(project(":sdk-android-fcm"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
