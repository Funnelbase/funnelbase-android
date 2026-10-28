// The optional Firebase glue for the SDK's push support: one messaging service, nothing else.
//
// It is a module of its own because :sdk-android has a dependency budget — kotlinx-serialization
// and nothing more — and Firebase is a large dependency to put in every host app, including the
// apps that deliver push through a gateway of their own with an `opaque` token. An app that does
// use Firebase adds this module and writes no push code at all.
//
// As with :sdk-android, no separate Kotlin Android plugin: AGP 9 brings Kotlin itself.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.funnelbase.sdk.fcm"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
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
    // `api` rather than `implementation`: an app adding this module is adding push, and it calls
    // the SDK's own surface to initialise and to route a tap.
    api(project(":sdk-android"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
