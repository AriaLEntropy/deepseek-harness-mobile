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

    js(IR) {
        browser()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":core-platform"))
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyVersion()}")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("net.shantu.kuiklysqlite:kuiklySqlite:1.0.0")
            }
        }
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("net.shantu.kuiklysqlite:kuiklySqlite:1.0.0")
            }
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

android {
    namespace = "com.example.dsh.corelog"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
}
