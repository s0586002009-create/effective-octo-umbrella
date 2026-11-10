plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val signingPath = System.getenv("KEYSTORE_PATH")
val signingStorePassword = System.getenv("KEYSTORE_PASSWORD")
val signingAlias = System.getenv("KEY_ALIAS")
val signingKeyPassword = System.getenv("KEY_PASSWORD")
android {
    namespace = "com.screenprank"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.screenprank"
        minSdk = 21
        targetSdk = 34
        versionCode = 6
        versionName = "0.6"
    }
    signingConfigs {
        create("release") {
            if (!signingPath.isNullOrBlank()) {
                storeFile = file(signingPath)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
