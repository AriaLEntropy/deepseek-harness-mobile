plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    kotlin("android").version("2.1.21").apply(false)
    kotlin("multiplatform").version("2.1.21").apply(false)
    id("com.google.devtools.ksp").version("2.1.21-2.0.1").apply(false)

}

buildscript {
    dependencies {
        classpath(BuildPlugin.kuikly)
    }
}

// ---------------------------------------------------------------------------
// 架构分层校验（fitness function）
//
// 分层（按包）：
//   UI 页面层   com.example.dsh.ui.<feature>   home / chat / session / settings / ...
//   UI 组件层   com.example.dsh.ui.rendering
//   数据/领域层 com.example.dsh.<domain>        host / storage / session / message / ...
//
// 规则：
//   R1 数据/领域层不得依赖 UI 层（com.example.dsh.ui.*）。
//   R2 UI 组件层（ui.rendering）不得依赖 UI 页面层，否则无法孵化通用组件。
//
// 依赖方向应为：数据/领域 ← 组件 ← 页面。
// 跑法：./gradlew checkLayering
// ---------------------------------------------------------------------------
val dshUiPagePrefixes = listOf(
    "com.example.dsh.ui.home",
    "com.example.dsh.ui.connection",
    "com.example.dsh.ui.log",
    "com.example.dsh.ui.models",
    "com.example.dsh.ui.plugin",
    "com.example.dsh.ui.settings",
    "com.example.dsh.ui.search",
    "com.example.dsh.ui.session",
    "com.example.dsh.ui.dev",
    "com.example.dsh.ui.web",
    "com.example.dsh.ui.export",
    "com.example.dsh.ui.interaction",
    "com.example.dsh.ui.voice",
)

tasks.register("checkLayering") {
    group = "verification"
    description = "校验包分层：数据/领域层不得依赖 UI，组件层不得依赖页面层。"

    val dshSources = fileTree(".") {
        include("*/src/**/*.kt")
        exclude("**/build/**")
        exclude("**/.gradle/**")
        exclude("**/oh_modules/**")
        exclude("**/*Test*/**")
    }
    inputs.files(dshSources).withPropertyName("dshSources")
    outputs.upToDateWhen { false }

    doLast {
        val rootDir = layout.projectDirectory.asFile
        val violations = mutableListOf<String>()
        dshSources.files.sortedBy { it.path }.forEach { file ->
            val lines = file.readLines()
            val pkg = lines.firstNotNullOfOrNull { line ->
                Regex("^package\\s+([\\w.]+)").find(line)?.groupValues?.get(1)
            } ?: return@forEach
            val isUi = pkg.startsWith("com.example.dsh.ui.")
            val isComponent = pkg.startsWith("com.example.dsh.ui.rendering")
            val rel = file.relativeTo(rootDir).path.replace('\\', '/')
            lines.forEachIndexed { index, line ->
                val imp = Regex("^import\\s+([\\w.]+)").find(line)?.groupValues?.get(1)
                    ?: return@forEachIndexed
                if (!imp.startsWith("com.example.dsh.")) return@forEachIndexed
                if (!isUi && imp.startsWith("com.example.dsh.ui.")) {
                    violations += "R1  $rel:${index + 1}  数据/领域层 -> UI：$imp"
                }
                if (isComponent && dshUiPagePrefixes.any { imp == it || imp.startsWith("$it.") }) {
                    violations += "R2  $rel:${index + 1}  组件层 -> 页面层：$imp"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("架构分层校验失败（${violations.size} 处）：")
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("依赖方向应为：数据/领域 ← 组件 ← 页面。")
                    appendLine("修复方式是把被依赖的声明下沉到正确的层，而不是放宽规则。")
                },
            )
        }
        logger.lifecycle("checkLayering: OK（无分层违规）")
    }
}
