plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.neapp.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neapp.tracker"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_KEY", "\"649dba57b904fcab2b82b0cd871cac32dcc084e0bb07c9cfb276db5209a82daf\"")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialização JSON
    implementation(libs.kotlinx.serialization.json)

    // GPS
    implementation(libs.play.services.location)

    // MQTT
    implementation(libs.hivemq.mqtt.client)

    // HTTP (polling de config)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}