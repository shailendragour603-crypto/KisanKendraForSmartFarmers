plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "shop.sabjiwala.kisankendra"
    compileSdk = 36

    defaultConfig {
        applicationId = "shop.sabjiwala.kisankendra"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("releaseConfig") {
            val keystoreFile = file("C:/Users/shail/Keys/kisankey.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "kisan1"
                keyAlias = System.getenv("KEY_ALIAS") ?: "kisan"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "kisan1"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("releaseConfig")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // 🔹 CORE DEPENDENCIES (Stable for SDK 36)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // 🔹 SWIPE TO REFRESH
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // 🔹 NATIVE NAVIGATION
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")

    // 🔹 NETWORKING & API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // 🔹 LOCATION SERVICES
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // 🔹 IMAGE LOADING (Stable Glide v4 for SDK 36)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 🔹 ADS & MARKETING
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.facebook.android:facebook-android-sdk:18.0.0")

    // 🔹 FIREBASE & AUTH
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // 🔹 GEMINI AI SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 🔹 TESTING
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}