package com.example.dsh.rendering

import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuiklybase.KuiklyWebView
import com.tencent.kuiklybase.WebView

/** 当前平台是否用 WebView + KaTeX 承载公式段落；未验证的平台返回 false，走 Unicode 降级。 */
internal expect fun dshMathWebViewSupported(): Boolean

/** 把公式段落切分结果拼成自包含的 KaTeX HTML（资源内联，无网络/文件依赖）。 */
internal object DshMathWeb {

    fun buildHtml(segments: List<DshMath.Segment>, dark: Boolean, fontSize: Float): String {
        val textColor = if (dark) 0xFFF5F6F7 else 0xFF1F1F23
        val background = if (dark) 0xFF151517 else 0xFFFFFFFF
        val payload = StringBuilder("[")
        segments.forEachIndexed { index, segment ->
            if (index > 0) payload.append(',')
            when (segment) {
                is DshMath.Segment.Text ->
                    payload.append("{\"t\":0,\"text\":").append(jsonString(segment.text)).append('}')
                is DshMath.Segment.Formula ->
                    payload.append("{\"t\":1,\"tex\":").append(jsonString(segment.source))
                        .append(",\"display\":").append(segment.block).append('}')
            }
        }
        payload.append(']')
        return buildString(DshKatexAssets.css.length + DshKatexAssets.js.length + 2048) {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">")
            append("<style>")
            append("html,body{margin:0;padding:0;background:").append(cssColor(background)).append(';')
            append("font-size:").append(fontSize).append("px;line-height:1.5;color:").append(cssColor(textColor)).append(';')
            append("word-break:break-word;overflow-wrap:anywhere;-webkit-text-size-adjust:100%;}")
            append(".katex-display{margin:.4em 0;text-align:center;}")
            append(DshKatexAssets.css)
            append("</style></head><body><div id=\"dsh-math-root\"></div>")
            append("<script id=\"dsh-math-data\" type=\"application/json\">").append(payload).append("</script>")
            append("<script>").append(DshKatexAssets.js).append("</script>")
            append("<script>(function(){try{")
            append("var data=JSON.parse(document.getElementById('dsh-math-data').textContent);")
            append("var root=document.getElementById('dsh-math-root');")
            append("for(var i=0;i<data.length;i++){var s=data[i];")
            append("if(s.t===0){root.appendChild(document.createTextNode(s.text));}")
            append("else{var el=document.createElement('span');root.appendChild(el);")
            append("try{katex.render(s.tex,el,{displayMode:!!s.display,throwOnError:false});}")
            append("catch(e){el.textContent=s.text||s.tex;}}}")
            append("}catch(e){}})();")
            append("window.__dshMathHeight=function(){var b=document.body,d=document.documentElement;")
            append("return Math.ceil(Math.max(b.scrollHeight,d.scrollHeight,b.offsetHeight,d.offsetHeight));};")
            append("</script></body></html>")
        }
    }

    /** JSON 字符串转义；`<`/`>`/`&` 以 \\u 形式转义，避免嵌入 script 标签被提前闭合。 */
    private fun jsonString(value: String): String {
        val sb = StringBuilder(value.length + 8)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u2028' -> sb.append("\\u2028")
                '\u2029' -> sb.append("\\u2029")
                '<' -> sb.append("\\u003c")
                '>' -> sb.append("\\u003e")
                '&' -> sb.append("\\u0026")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun cssColor(argb: Long): String =
        "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
}

/** 用 WebView + KaTeX 渲染含公式的段落；高度由 JS 测高后回填。 */
internal class DshMathParagraphView : ComposeView<DshMathParagraphAttr, ComposeEvent>() {
    private var webViewRef: ViewRef<KuiklyWebView>? = null
    private var htmlKey = ""
    private var htmlCache = ""

    override fun createAttr(): DshMathParagraphAttr = DshMathParagraphAttr()

    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            val html = ctx.html()
            WebView {
                ref { ctx.webViewRef = it }
                attr {
                    htmlContent(html)
                    javaScriptEnabled(true)
                    if (ctx.attr.contentWidth > 0f) width(ctx.attr.contentWidth)
                    height(if (ctx.attr.reportedHeight > 0f) ctx.attr.reportedHeight else DEFAULT_HEIGHT)
                }
                event {
                    onPageFinished { _ -> ctx.scheduleMeasure(0) }
                }
            }
        }
    }

    /**
     * KaTeX 的 vlist 用定位/零高元素排版，`getBoundingClientRect` 会低估高度，
     * 因此 JS 侧用 scrollHeight 取内容高度；再按间隔重测几次，覆盖字体/布局晚到的情况。
     */
    private fun scheduleMeasure(attempt: Int) {
        webViewRef?.view?.evaluateJavaScript("window.__dshMathHeight()") { result ->
            val height = result?.toFloatOrNull() ?: 0f
            if (height > 0f && height != attr.reportedHeight) {
                attr.reportedHeight = height
            }
        }
        if (attempt < MAX_MEASURE_ATTEMPTS) {
            setTimeout(pagerId, 250 * (attempt + 1)) { scheduleMeasure(attempt + 1) }
        }
    }

    private fun html(): String {
        val key = "${attr.dark}:${attr.fontSize}:${attr.content.hashCode()}"
        if (key != htmlKey) {
            htmlCache = DshMathWeb.buildHtml(DshMath.parseSegments(attr.content), attr.dark, attr.fontSize)
            htmlKey = key
        }
        return htmlCache
    }

    private companion object {
        const val DEFAULT_HEIGHT = 24f
        const val MAX_MEASURE_ATTEMPTS = 4
    }
}

internal class DshMathParagraphAttr : ComposeAttr() {
    var content: String by observable("")
    var dark: Boolean by observable(false)
    var fontSize: Float by observable(15f)
    var contentWidth: Float by observable(0f)
    var reportedHeight: Float by observable(0f)
}

internal fun ViewContainer<*, *>.DshMathParagraph(init: DshMathParagraphView.() -> Unit) {
    addChild(DshMathParagraphView(), init)
}
