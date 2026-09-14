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
                api(project(":ui-base"))
                api(project(":core-data"))
                api(project(":core-model"))
                api(project(":core-theme"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyVersion()}")
                implementation("com.tencent.kuiklybase:KuiklyMarkdown:1.0.6-2.1.21")
                implementation("com.tencent.kuiklybase:KuiklyWebview:1.0.1-2.0.21")
            }
        }
        val androidMain by getting {
            dependencies {
                api("com.tencent.kuikly-open:core-render-android:${Version.getKuiklyVersion()}")
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
        val jsMain by getting
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
