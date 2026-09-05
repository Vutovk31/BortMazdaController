plugins {
    id("com.android.application")
}

android {
    namespace = "ru.mdc.displaycontroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.mdc.displaycontroller"
        minSdk = 26
        targetSdk = 31
        versionCode = 10101
        versionName = "1.0.1-internal-1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".internal"
            versionNameSuffix = ""
            buildConfigField("boolean", "CAN_WRITE", "false")
            buildConfigField("String", "SAFETY_PROFILE", "\"READ_ONLY_1_0_1\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "CAN_WRITE", "false")
            buildConfigField("String", "SAFETY_PROFILE", "\"READ_ONLY_1_0_1\"")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
