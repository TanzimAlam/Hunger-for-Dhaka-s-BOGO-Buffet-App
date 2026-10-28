import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// An optional Google Places key can be baked in so the app needs no in-app setup.
// Put PLACES_API_KEY=... in local.properties (not committed) or the environment.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val placesApiKey = (localProperties.getProperty("PLACES_API_KEY") ?: System.getenv("PLACES_API_KEY") ?: "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
// Optional Facebook Page token with Page Public Content Access, so venue posts are read live.
val facebookPageToken = (localProperties.getProperty("FACEBOOK_PAGE_TOKEN")
    ?: System.getenv("FACEBOOK_PAGE_TOKEN") ?: "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.buffetbogo.hunter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.buffetbogo.hunter"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PLACES_API_KEY", "\"$placesApiKey\"")
        buildConfigField("String", "FACEBOOK_PAGE_TOKEN", "\"$facebookPageToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM tests, so the bundled catalogue is validated off-device.
    testImplementation("org.json:json:20240303")
}
