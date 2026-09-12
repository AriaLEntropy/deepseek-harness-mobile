package com.example.dsh.rendering

/**
 * 轻量 LaTeX 预处理：把 `$...$` / `$$...$$` 转成 Unicode 近似公式文本。
 *
 * KuiklyMarkdown 的解析器能识别 INLINE_MATH / BLOCK_MATH 节点，但渲染器不处理它们；
 * 这里在送入渲染前把闭合公式替换为可读文本，未闭合/非法公式原样保留（安全降级）。
 * 这不是完整排版引擎，只覆盖常见符号、上下标、分式、根式与文本命令。
 *
 * 解析规则（对齐 CommonMark 数学扩展的常用约定）：
 * - 行内 `$...$`：内容不得含换行，且首尾不能是空白，避免把 “$5 和 $10” 误判为公式。
 * - 块级 `$$...$$`：内容可含空白/换行，替换为独占段落，实现独立排版。
 * - 反引号行内代码、``` 围栏代码、`\$` 转义均不参与替换。
 */
internal object DshMath {

    /** 文本与公式的切分结果，供 WebView 公式段落渲染使用。 */
    internal sealed interface Segment {
        data class Text(val text: String) : Segment
        data class Formula(val source: String, val block: Boolean) : Segment
    }

    /** 命令表：按 key 长度降序处理，避免 `\le` 抢先匹配 `\left` 这类前缀冲突。 */
    private val commands: List<Pair<String, String>> = linkedMapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
        "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ",
        "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς",
        "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
        "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
        "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓", "cdot" to "·", "ast" to "∗",
        "star" to "⋆", "circ" to "∘", "bullet" to "•", "oplus" to "⊕", "otimes" to "⊗",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠",
        "ll" to "≪", "gg" to "≫", "approx" to "≈", "sim" to "∼", "simeq" to "≃", "equiv" to "≡",
        "propto" to "∝", "infty" to "∞", "partial" to "∂", "nabla" to "∇",
        "sum" to "∑", "prod" to "∏", "coprod" to "∐", "int" to "∫", "iint" to "∬", "oint" to "∮",
        "sqrt" to "√", "angle" to "∠", "degree" to "°", "prime" to "′",
        "rightarrow" to "→", "leftarrow" to "←", "leftrightarrow" to "↔",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
        "longrightarrow" to "⟶", "longleftarrow" to "⟵", "mapsto" to "↦", "to" to "→",
        "uparrow" to "↑", "downarrow" to "↓",
        "in" to "∈", "notin" to "∉", "ni" to "∋", "subset" to "⊂", "subseteq" to "⊆",
        "supset" to "⊃", "supseteq" to "⊇", "cup" to "∪", "cap" to "∩", "setminus" to "∖",
        "emptyset" to "∅", "varnothing" to "∅",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄", "neg" to "¬", "lnot" to "¬",
        "therefore" to "∴", "because" to "∵",
        "ldots" to "…", "cdots" to "…", "vdots" to "⋮", "ddots" to "⋱",
        "quad" to " ", "qquad" to "  ", "left" to "", "right" to "", "begin" to "", "end" to "",
    ).entries.sortedByDescending { it.key.length }.map { it.key to it.value }

    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶',
        '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽',
        ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ',
    )

    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆',
        '7' to '₇', '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍',
        ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'n' to 'ₙ', 'i' to 'ᵢ',
    )

    /** 供 Unicode 降级路径调用：转换闭合公式，跳过行内代码、``` 围栏代码与转义 `$`。 */
    fun transform(markdown: String): String {
        if (markdown.indexOf('$') < 0) return markdown
        val out = StringBuilder(markdown.length)
        for (segment in parseSegments(markdown)) {
            when (segment) {
                is Segment.Text -> out.append(segment.text)
                is Segment.Formula -> if (segment.block) {
                    if (!out.endsWith("\n\n")) out.append("\n\n")
                    out.append(render(segment.source)).append("\n\n")
                } else {
                    out.append(render(segment.source))
                }
            }
        }
        return out.toString()
    }

    /** 按公式边界切分文本；代码围栏、行内代码与转义 `$` 保持为普通文本。 */
    fun parseSegments(markdown: String): List<Segment> {
        if (markdown.indexOf('$') < 0) return listOf(Segment.Text(markdown))
        val segments = ArrayList<Segment>()
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                segments.add(Segment.Text(text.toString()))
                text.clear()
            }
        }
        val length = markdown.length
        var i = 0
        var inFence = false
        while (i < length) {
            val atLineStart = i == 0 || markdown[i - 1] == '\n'
            if (atLineStart && markdown.startsWith("```", i)) {
                inFence = !inFence
                text.append("```")
                i += 3
                continue
            }
            if (inFence) {
                text.append(markdown[i])
                i++
                continue
            }
            val c = markdown[i]
            if (c == '`') {
                var j = i
                while (j < length && markdown[j] == '`') j++
                val ticks = markdown.substring(i, j)
                val close = markdown.indexOf(ticks, j)
                if (close >= 0) {
                    text.append(markdown, i, close + ticks.length)
                    i = close + ticks.length
                } else {
                    text.append(ticks)
                    i = j
                }
                continue
            }
            if (c == '\\' && i + 1 < length && markdown[i + 1] == '$') {
                text.append("\\$")
                i += 2
                continue
            }
            if (c == '$') {
                val block = markdown.startsWith("$$", i)
                val delimiter = if (block) "$$" else "$"
                val searchFrom = i + delimiter.length
                val close = markdown.indexOf(delimiter, searchFrom)
                if (close > searchFrom) {
                    val source = markdown.substring(searchFrom, close)
                    if (isFormula(source, block)) {
                        flushText()
                        segments.add(Segment.Formula(source.trim(), block))
                        i = close + delimiter.length
                        continue
                    }
                }
            }
            text.append(c)
            i++
        }
        flushText()
        return segments
    }

    /** 该段文本是否包含可渲染公式。 */
    fun containsFormula(markdown: String): Boolean =
        parseSegments(markdown).any { it is Segment.Formula }

    /** 行内公式禁止换行与首尾空白，降低把货币符号误判为公式的概率。 */
    private fun isFormula(source: String, block: Boolean): Boolean {
        if (source.isBlank()) return false
        if (block) return true
        if (source.contains('\n')) return false
        return !source.first().isWhitespace() && !source.last().isWhitespace()
    }

    /** 把一段 LaTeX 源码转成 Unicode 近似文本。 */
    fun render(latex: String): String {
        var s = latex
        s = Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}""").replace(s) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
        s = Regex("""\\dfrac\{([^{}]*)\}\{([^{}]*)\}""").replace(s) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
        s = Regex("""\\tfrac\{([^{}]*)\}\{([^{}]*)\}""").replace(s) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
        s = Regex("""\\sqrt\[[^\]]*\]\{([^{}]*)\}""").replace(s) { "√(${it.groupValues[1]})" }
        s = Regex("""\\sqrt\{([^{}]*)\}""").replace(s) { "√(${it.groupValues[1]})" }
        s = Regex("""\\text\{([^{}]*)\}""").replace(s) { it.groupValues[1] }
        s = Regex("""\\mathrm\{([^{}]*)\}""").replace(s) { it.groupValues[1] }
        s = Regex("""\\mathbf\{([^{}]*)\}""").replace(s) { it.groupValues[1] }
        s = Regex("""\\operatorname\{([^{}]*)\}""").replace(s) { it.groupValues[1] }
        for ((cmd, unicode) in commands) s = s.replace("\\$cmd", unicode)
        s = scripts(s, '^', superscripts)
        s = scripts(s, '_', subscripts)
        s = s.replace("\\\\", " ")
        s = Regex("""\\([a-zA-Z]+)""").replace(s) { it.groupValues[1] }
        s = s.replace("\\,", " ").replace("\\;", " ").replace("\\:", " ").replace("\\!", "")
        s = s.replace("~", " ")
        s = s.replace("\\{", "{").replace("\\}", "}")
        s = s.replace("{", "").replace("}", "")
        return s.trim()
    }

    private fun scripts(source: String, marker: Char, table: Map<Char, Char>): String {
        var s = Regex("""\Q$marker\E\{([^{}]*)\}""").replace(source) { m ->
            m.groupValues[1].map { table[it] ?: it }.joinToString("")
        }
        s = Regex("""\Q$marker\E([A-Za-z0-9+\-=()])""").replace(s) { m ->
            m.groupValues[1].map { table[it] ?: it }.joinToString("")
        }
        return s
    }
}
