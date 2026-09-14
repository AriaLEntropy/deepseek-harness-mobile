pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
        maven {
            url = uri("https://hd-l.github.io/KuiklyUISqlite")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
        maven {
            url = uri("https://hd-l.github.io/KuiklyUISqlite")
        }
    }
}

rootProject.name = "DSH"
include(":core-model")
include(":core-platform")
include(":core-log")
include(":core-theme")
include(":core-data")
include(":ui-base")
include(":ui-kit")
include(":ui-voice")
include(":ui-export")
include(":ui-settings")
include(":ui-web")
include(":ui-dev")
include(":androidApp")
include(":shared")
include(":h5App")
include(":miniApp")
