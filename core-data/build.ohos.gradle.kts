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
        val commonMain by getting {
            dependencies {
                api(project(":core-model"))
                api(project(":core-platform"))
                api(project(":core-log"))
                api(project(":core-theme"))
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyOhosVersion()}")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-KBA-002")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.1-KBA-003")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1-KBA-003")
            }
        }
        val ohosArm64Main by getting
        val ohosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("net.shantu.kuiklysqlite:kuiklySqlite:1.0.0")
                implementation("com.tencent.kuiklybase:network:0.0.4")
            }
            ohosArm64Main.dependsOn(this)
        }
        val androidMain by getting {
            dependencies {
                api("com.tencent.kuikly-open:core-render-android:${Version.getKuiklyOhosVersion()}")
            }
        }
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

android {
    namespace = "com.example.dsh.coredata"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
}
