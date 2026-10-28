// No `org.jetbrains.kotlin.android` here: AGP 9 has built-in Kotlin support and rejects the
// standalone plugin. The Kotlin version comes from AGP; only the serialization compiler plugin
// is applied on top.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.funnelbase.sdk"
    compileSdk = 36

    defaultConfig {
        // API 26 for java.time without desugaring. Below that we would ship a second date
        // library into every host app for a rounding error's worth of device coverage.
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Android framework classes are stubs in unit tests; returning defaults instead of
        // throwing lets pure-logic classes (EventQueue, SessionManager) be tested on the JVM.
        unitTests.isReturnDefaultValues = true
    }
}

// Compiled for apps on an older Kotlin than this build's: see `kotlinConsumerLanguage` in
// gradle/libs.versions.toml.
kotlin {
    coreLibrariesVersion = libs.versions.kotlinConsumerStdlib.get()
    compilerOptions {
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(libs.versions.kotlinConsumerLanguage.get())
        apiVersion = languageVersion
    }
}

// Published to Maven Central through the Central Portal (RELEASING.md). Coordinates and the POM come
// from gradle.properties. A release is uploaded and left for a maintainer to publish in the
// portal, never released automatically. Signed only when the release keys are present, so
// `publishToMavenLocal` works on any machine.
mavenPublishing {
    publishToMavenCentral(automaticRelease = false)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}

dependencies {
    // Deliberately minimal. This library is linked into someone else's app, so every transitive
    // dependency is a version conflict we are handing them. No OkHttp, no Retrofit — the
    // uploader uses HttpURLConnection from the platform. Not even androidx.core: its only use
    // here was the `edit {}` extension, and pulling it in would drag this module's compileSdk
    // along with androidx's release cadence.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
