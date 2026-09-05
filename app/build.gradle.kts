plugins {
    id("com.android.application")
}

android {
    namespace = "ru.mdc.displaycontroller"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.mdc.displaycontroller"
        minSdk = 26
        targetSdk = 31
        versionCode = 1000100
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "CAN_WRITE", "false")
        buildConfigField("boolean", "OBD_WRITE", "false")
        buildConfigField("boolean", "OEM_WRITE", "false")
        buildConfigField("boolean", "RAW_CAN_WRITE", "false")
        buildConfigField("boolean", "UNKNOWN_BINDER_CALL", "false")
        buildConfigField("boolean", "UNKNOWN_BROADCAST_SEND", "false")
        buildConfigField("boolean", "DEVICE_NODE_WRITE", "false")
        buildConfigField("String", "MDC_SAFETY_PROFILE", "\"READ_ONLY_1_0_1\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("internal") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.8.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
