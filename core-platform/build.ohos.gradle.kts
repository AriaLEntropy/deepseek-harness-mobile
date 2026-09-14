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

    ohosArm64 {
        compilations.getByName("main").compileTaskProvider.configure {
            compilerOptions {
                optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
                optIn.add("kotlin.experimental.ExperimentalNativeApi")
            }
        }
    }

    sourceSets {
        val commonMain by getting
    }
}

android {
    namespace = "com.example.dsh.coreplatform"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
}
