plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("maven-publish")

}

val KEY_PAGE_NAME = "pageName"

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishLibraryVariants("release")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            freeCompilerArgs = freeCompilerArgs + getCommonCompilerArgs()
            isStatic = true
            license = "MIT"
        }
    }

    ohosArm64 {
        binaries.sharedLib {
            linkerOpts(
                "-L${rootProject.projectDir}/ohosApp/entry/libs/arm64-v8a",
                "-lpbcurlwrapper",
            )
        }
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
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuiklybase:KuiklyMarkdown:1.0.6-2.0.21-ohos")
                implementation("com.tencent.kuiklybase:KuiklyWebview:1.0.1-2.0.21-KBA-010")
                // HarmonyOS KN variant from KuiklyBase-platform; stock 1.10.1 has no ohos_arm64.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-KBA-002")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.1-KBA-003")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1-KBA-003")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
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
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }
}

group = "com.example.dsh"
version = System.getenv("kuiklyBizVersion") ?: "1.0.0"

publishing {
    repositories {
        maven {
            credentials {
                username = System.getenv("mavenUserName") ?: ""
                password = System.getenv("mavenPassword") ?: ""
            }
            rootProject.properties["mavenUr?"]?.toString()?.let { url = uri(it) }
        }
    }
}

ksp {
    arg(KEY_PAGE_NAME, getPageName())
}

dependencies {
    compileOnly("com.tencent.kuikly-open:core-ksp:${Version.getKuiklyOhosVersion()}") {
        add("kspAndroid", this)
        add("kspIosArm64", this)
        add("kspIosX64", this)
        add("kspIosSimulatorArm64", this)
        add("kspOhosArm64", this)
    }
}

android {
    namespace = "com.example.dsh.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
    sourceSets {
        named("main") {
            assets.srcDirs("src/commonMain/assets")
        }
    }
}

fun getPageName(): String {
    return (project.properties[KEY_PAGE_NAME] as? String) ?: ""
}

fun getCommonCompilerArgs(): List<String> {
    return listOf(
        "-Xallocator=std"
    )
}

fun getLinkerArgs(): List<String> {
    return listOf()
}

val nativeLibsDir = rootProject.file("ohosApp/entry/libs/arm64-v8a")
val pbcurlwrapperSo = nativeLibsDir.resolve("libpbcurlwrapper.so")
val opensslSo = nativeLibsDir.resolve("libopenssl.so")
val downloadPbcurlwrapper = tasks.register("downloadPbcurlwrapper") {
    outputs.files(pbcurlwrapperSo, opensslSo)
    doLast {
        nativeLibsDir.mkdirs()
        fun fetch(file: java.io.File, url: String, minBytes: Long) {
            if (file.exists() && file.length() > minBytes) {
                return
            }
            exec {
                commandLine("curl", "-L", "--fail", "--retry", "3", "-o", file.absolutePath, url)
            }
            if (!file.exists() || file.length() < minBytes) {
                throw GradleException("downloaded ${file.name} is too small (${file.length()} bytes)")
            }
        }
        fetch(
            pbcurlwrapperSo,
            "https://github.com/Tencent-TDS/KuiklyBase-components/raw/master/NetworkKMM/network/libs/libpbcurlwrapper.so",
            100_000L,
        )
        fetch(
            opensslSo,
            "https://github.com/Tencent-TDS/KuiklyBase-components/raw/master/NetworkKMM/ohosApp/pbcurlwrapper/libs/arm64-v8a/libopenssl.so",
            1_000_000L,
        )
    }
}

tasks.configureEach {
    if (name.contains("OhosArm64") && (name.startsWith("link") || name.startsWith("compileKotlin"))) {
        dependsOn(downloadPbcurlwrapper)
    }
}
