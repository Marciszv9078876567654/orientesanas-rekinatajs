plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.orientesanasrekinatajs"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.orientesanasrekinatajs"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        androidResources {
            localeFilters += listOf("en", "lv")
        }

        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")

            optimization {
                enable = true
            }
        }
        if (providers.gradleProperty("testOptimizedOcr").orNull == "true") {
            create("ocrCheck") {
                initWith(getByName("release"))
                applicationIdSuffix = ".ocrcheck"
                signingConfig = signingConfigs.getByName("debug")
                matchingFallbacks += "release"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    if (providers.gradleProperty("includeMapExampleTests").orNull == "true") {
        sourceSets.getByName("androidTest").assets.srcDir("../docs/map-examples")
        sourceSets.getByName("androidTest").java.srcDir("src/mapExampleTest/java")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.opencv)

    // DataStore (user preferences)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)

    // Room (local data layer)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
