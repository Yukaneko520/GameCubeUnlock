
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.yuka.gamecube.tools"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.2.12479018"
    externalNativeBuild { ndkBuild { path = file("src/main/cpp/Android.mk") } }
    defaultConfig {
        applicationId = "com.yuka.gamecube.tools"
        minSdk = 34
        targetSdk = 36
        versionCode = 17
        versionName = "0.1.36"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
if (providers.gradleProperty("uiScreenshots").orNull == "true") {
    apply(plugin = "app.cash.paparazzi")
    android.sourceSets.getByName("test").kotlin.directories.add("src/screenshotTest/java")
    dependencies.add("testImplementation", "app.cash.paparazzi:paparazzi:2.0.0-alpha05.1")
}
