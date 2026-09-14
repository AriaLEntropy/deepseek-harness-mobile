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

val buildFileName = "build.ohos.gradle.kts"
rootProject.buildFileName = buildFileName

include(":core-model")
project(":core-model").buildFileName = buildFileName

include(":core-platform")
project(":core-platform").buildFileName = buildFileName

include(":core-log")
project(":core-log").buildFileName = buildFileName

include(":core-theme")
project(":core-theme").buildFileName = buildFileName

include(":core-data")
project(":core-data").buildFileName = buildFileName

include(":ui-base")
project(":ui-base").buildFileName = buildFileName

include(":ui-kit")
project(":ui-kit").buildFileName = buildFileName

include(":ui-voice")
project(":ui-voice").buildFileName = buildFileName

include(":ui-export")
project(":ui-export").buildFileName = buildFileName

include(":ui-settings")
project(":ui-settings").buildFileName = buildFileName

include(":ui-web")
project(":ui-web").buildFileName = buildFileName

include(":ui-dev")
project(":ui-dev").buildFileName = buildFileName

include(":shared")
project(":shared").buildFileName = buildFileName
