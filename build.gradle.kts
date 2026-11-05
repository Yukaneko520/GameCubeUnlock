// Host-only visual verification; ordinary offline builds do not resolve screenshot tooling.
buildscript {
    if (gradle.startParameter.projectProperties["uiScreenshots"] == "true") {
        repositories { google(); mavenCentral(); gradlePluginPortal() }
        dependencies { classpath("app.cash.paparazzi:paparazzi-gradle-plugin:2.0.0-alpha05.1") }
    }
}
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
