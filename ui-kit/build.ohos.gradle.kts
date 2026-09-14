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
                api(project(":ui-base"))
                api(project(":core-data"))
                api(project(":core-model"))
                api(project(":core-theme"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-KBA-002")
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuiklybase:KuiklyMarkdown:1.0.6-2.0.21-ohos")
                implementation("com.tencent.kuiklybase:KuiklyWebview:1.0.1-2.0.21-KBA-010")
            }
        }
        val ohosArm64Main by getting
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
    namespace = "com.example.dsh.uikit"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
}
