package io.github.mangi.eta.ui.markdown

/**
 * 把模型常用的 `$...$` / `$$...$$` 数学片段转成可读文本。
 *
 * GFM 解析器默认不会把裸 `$` 识别为数学节点；如果不预处理，Compose 会把 `$G$`、
 * `\frac{...}` 原样显示。这里只处理“像数学”的内容（含反斜杠、运算符、上下标或
 * 单个符号），保留普通货币文本不变；代码围栏和行内代码不处理。
 */
internal object DollarMath {
    fun normalize(source: String): String {
        if ('$' !in source) return source
        val out = StringBuilder(source.length)
        var index = 0
        var fence: String? = null
        while (index < source.length) {
            val lineStart = index == 0 || source[index - 1] == '\n'
            if (lineStart) {
                val lineEnd = source.indexOf('\n', index).let { if (it < 0) source.length else it }
                val line = source.substring(index, lineEnd)
                val marker = fenceMarker(line)
                if (marker != null) {
                    val open = fence
                    if (open == null) {
                        fence = marker
                    } else if (marker.first() == open.first() && marker.length >= open.length &&
                        line.trim() == marker
                    ) {
                        fence = null
                    }
                }
            }
            if (fence != null) {
                out.append(source[index])
                index += 1
                continue
            }
            val char = source[index]
            if (char == '`') {
                val run = source.runLength(index, '`')
                val end = codeSpanEnd(source, index + run, run)
                if (end == null) {
                    out.append(source, index, index + run)
                    index += run
                } else {
                    out.append(source, index, end)
                    index = end
                }
                continue
            }
            if (char == '\\') {
                val end = (index + 2).coerceAtMost(source.length)
                out.append(source, index, end)
                index = end
                continue
            }
            if (char != '$') {
                out.append(char)
                index += 1
                continue
            }
            val run = source.runLength(index, '$')
            if (run > 2) {
                out.append(source, index, index + run)
                index += run
                continue
            }
            val close = findClose(source, index + run, run)
            if (close != null) {
                val content = source.substring(index + run, close)
                if (isMathLike(content)) {
                    out.append(LatexSymbols.normalize(content))
                    index = close + run
                    continue
                }
            }
            out.append(source, index, index + run)
            index += run
        }
        return out.toString()
    }

    private fun isMathLike(content: String): Boolean {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains('\\') || trimmed.any { it in "=^_{}<>±×÷≤≥≠∞∑∫√→" }) return true
        if (trimmed.any { it in 'α'..'ω' || it in 'Α'..'Ω' }) return true
        // 单个变量（$G$、$H$）是数学；带空格/中文的普通货币句子不算。
        if (trimmed.length <= 4 && trimmed.all { it.isLetterOrDigit() }) return true
        if (trimmed.all { it.isDigit() || it in "+-./()" }) return true
        return false
    }

    /** 在下一个空行之前找同长度的 `$` 串，跳过转义。 */
    private fun findClose(source: String, from: Int, run: Int): Int? {
        var index = from
        while (index < source.length - run + 1) {
            if (source[index] == '\n' && blankLineAfter(source, index)) return null
            if (source[index] == '\\') {
                index += 2
                continue
            }
            if (source[index] == '$') {
                val length = source.runLength(index, '$')
                if (length == run) return index
                index += length
                continue
            }
            index += 1
        }
        return null
    }

    private fun codeSpanEnd(source: String, from: Int, run: Int): Int? {
        var index = from
        while (index < source.length) {
            if (source[index] == '\n' && blankLineAfter(source, index)) return null
            if (source[index] == '`') {
                val length = source.runLength(index, '`')
                if (length == run) return index + length
                index += length
            } else {
                index += 1
            }
        }
        return null
    }

    private fun blankLineAfter(source: String, newline: Int): Boolean {
        val next = source.indexOf('\n', newline + 1).let { if (it < 0) source.length else it }
        return source.substring(newline + 1, next).isBlank()
    }

    private fun fenceMarker(line: String): String? {
        val trimmed = line.trimStart(' ')
        if (line.length - trimmed.length > 3) return null
        val char = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val run = trimmed.runLength(0, char)
        return if (run >= 3) char.toString().repeat(run) else null
    }

    private fun String.runLength(start: Int, char: Char): Int {
        var end = start
        while (end < length && this[end] == char) end += 1
        return end - start
    }
}
