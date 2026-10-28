rootProject.name = "funnelbase-android"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

// The SDK, published as com.funnel-base:funnelbase-android.
include(":sdk-android")
// The optional Firebase glue: one messaging service over the SDK's push API. Published as
// com.funnel-base:funnelbase-android-fcm.
include(":sdk-android-fcm")
// The demo app consumes both from source, so a change to the SDK is tried on a device before it
// is released.
include(":demo-android")
