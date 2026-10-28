// Every plugin any module uses is declared here with `apply false`, which pins one version on
// the build classpath for all of them.
plugins {
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.maven.publish) apply false
}
