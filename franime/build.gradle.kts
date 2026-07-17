plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization") version "1.9.0"
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension.fr.franime"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }

    buildFeatures {
        compose = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Aniyomi API
    compileOnly("com.github.jmir1:aniyomi-api:1.0.0")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Video Extractors
    compileOnly("com.github.jmir1:aniyomi-lib-sendvidextractor:1.0.0")
    compileOnly("com.github.jmir1:aniyomi-lib-sibnetextractor:1.0.0")
    compileOnly("com.github.jmir1:aniyomi-lib-vidmolyextractor:1.0.0")
    compileOnly("com.github.jmir1:aniyomi-lib-vkextractor:1.0.0")
    
    // Utilities
    implementation("com.github.Keiyoushi:keiyoushi-utils:1.0.0")
    implementation("uy.kohesive.injekt:injekt-core:1.16.1")
}
