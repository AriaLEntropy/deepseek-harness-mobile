plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    ohosArm64()

    sourceSets {
        val commonMain by getting
    }
}

android {
    namespace = "com.example.dsh.coremodel"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
}
