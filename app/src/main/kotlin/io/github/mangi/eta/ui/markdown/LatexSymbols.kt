package io.github.mangi.eta.ui.markdown

/**
 * 轻量 LaTeX 符号归一化。
 *
 * 不引入完整公式排版引擎：把模型最常见的反斜杠命令转换成 Unicode 数学符号，并展开
 * `\frac`/`\sqrt`/`\text` 等结构，使行内公式在 Compose 文本中可读、可复制。未知命令
 * 原样保留，避免把不认识的宏错误地吞掉。
 */
internal object LatexSymbols {
    private val commands: List<Pair<String, String>> = listOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\Gamma" to "Γ",
        "\\delta" to "δ", "\\Delta" to "Δ", "\\epsilon" to "ε", "\\varepsilon" to "ε",
        "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ", "\\Theta" to "Θ",
        "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ",
        "\\Lambda" to "Λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\Xi" to "Ξ",
        "\\pi" to "π", "\\Pi" to "Π", "\\rho" to "ρ", "\\sigma" to "σ", "\\Sigma" to "Σ",
        "\\tau" to "τ", "\\upsilon" to "υ", "\\Upsilon" to "Υ", "\\phi" to "φ",
        "\\varphi" to "φ", "\\Phi" to "Φ", "\\chi" to "χ", "\\psi" to "ψ",
        "\\Psi" to "Ψ", "\\omega" to "ω", "\\Omega" to "Ω",
        "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇", "\\sum" to "∑",
        "\\prod" to "∏", "\\coprod" to "∐", "\\int" to "∫", "\\iint" to "∬",
        "\\iiint" to "∭", "\\oint" to "∮", "\\pm" to "±", "\\mp" to "∓",
        "\\times" to "×", "\\div" to "÷", "\\cdot" to "·", "\\ast" to "∗",
        "\\star" to "⋆", "\\oplus" to "⊕", "\\otimes" to "⊗", "\\ominus" to "⊖",
        "\\le" to "≤", "\\leq" to "≤", "\\ge" to "≥", "\\geq" to "≥",
        "\\ne" to "≠", "\\neq" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
        "\\sim" to "∼", "\\simeq" to "≃", "\\cong" to "≅", "\\propto" to "∝",
        "\\ll" to "≪", "\\gg" to "≫", "\\in" to "∈", "\\notin" to "∉",
        "\\ni" to "∋", "\\subset" to "⊂", "\\supset" to "⊃", "\\subseteq" to "⊆",
        "\\supseteq" to "⊇", "\\cup" to "∪", "\\cap" to "∩", "\\setminus" to "∖",
        "\\emptyset" to "∅", "\\varnothing" to "∅", "\\forall" to "∀",
        "\\exists" to "∃", "\\nexists" to "∄", "\\therefore" to "∴",
        "\\because" to "∵", "\\neg" to "¬", "\\land" to "∧", "\\lor" to "∨",
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←",
        "\\leftrightarrow" to "↔", "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐",
        "\\Leftrightarrow" to "⇔", "\\mapsto" to "↦", "\\uparrow" to "↑",
        "\\downarrow" to "↓", "\\longrightarrow" to "⟶", "\\longleftarrow" to "⟵",
        "\\perp" to "⊥", "\\parallel" to "∥", "\\angle" to "∠",
        "\\triangle" to "△", "\\square" to "□", "\\odot" to "⊙",
        "\\degree" to "°", "\\circ" to "°", "\\prime" to "′", "\\hbar" to "ℏ",
        "\\ell" to "ℓ", "\\Re" to "ℜ", "\\Im" to "ℑ", "\\aleph" to "ℵ",
        "\\dots" to "…", "\\ldots" to "…", "\\cdots" to "⋯", "\\vdots" to "⋮",
        "\\ddots" to "⋱", "\\mathbb{R}" to "ℝ", "\\mathbb{N}" to "ℕ",
        "\\mathbb{Z}" to "ℤ", "\\mathbb{Q}" to "ℚ", "\\mathbb{C}" to "ℂ",
        "\\lim" to "lim", "\\log" to "log", "\\ln" to "ln", "\\sin" to "sin",
        "\\cos" to "cos", "\\tan" to "tan", "\\exp" to "exp", "\\max" to "max",
        "\\min" to "min", "\\det" to "det", "\\arg" to "arg",
    ).sortedByDescending { it.first.length }

    private val spacing = Regex("""\\(?:left|right|,|;|!|quad|qquad)""")
    private val limit = Regex("""\\lim\s*_\s*\{([^{}]*)\}""")
    private val superscript = Regex("""\^\{([^{}]*)\}|\^([A-Za-z0-9]{1,4})""")
    private val subscript = Regex("""_\{([^{}]*)\}|_([A-Za-z0-9]{1,4})""")
    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ',
        'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ', 'k' to 'ᵏ', 'l' to 'ˡ',
        'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ',
        'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
    )
    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'n' to 'ₙ',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        'l' to 'ₗ', 'm' to 'ₘ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ',
        't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
    )

    fun normalize(raw: String): String {
        var value = raw
        listOf("\\frac", "\\dfrac", "\\tfrac").forEach { command ->
            value = replaceCommand(value, command, 2) {
                "(${normalize(it[0])})/(${normalize(it[1])})"
            }
        }
        value = replaceCommand(value, "\\sqrt", 1) { "√(${normalize(it[0])})" }
        value = limit.replace(value) { match ->
            val inside = normalize(match.groupValues[1]).replace(" ", "")
            "lim($inside)"
        }
        listOf("\\text", "\\mathrm", "\\mathbf", "\\mathit", "\\operatorname").forEach { command ->
            value = replaceCommand(value, command, 1) { normalize(it[0]) }
        }
        // Reduce commands before mapping sub/superscript characters; otherwise a command
        // inside `_{...}` is split into separate subscript letters.
        commands.forEach { (command, symbol) -> value = value.replace(command, symbol) }
        value = superscript.replace(value) { m ->
            val body = m.groupValues[1].ifEmpty { m.groupValues[2] }
            body.map { superscripts[it] ?: it }.joinToString("")
        }
        value = subscript.replace(value) { m ->
            val body = m.groupValues[1].ifEmpty { m.groupValues[2] }
            body.filterNot { it.isWhitespace() }.map { subscripts[it] ?: it }.joinToString("")
        }
        value = spacing.replace(value, "")
        return value.trim()
    }

    /** 读取命令后的 `{...}` 参数，支持嵌套花括号；无花括号时读取一个片段。 */
    private fun replaceCommand(
        source: String,
        command: String,
        arity: Int,
        render: (List<String>) -> String,
    ): String {
        var value = source
        var search = 0
        while (true) {
            val start = value.indexOf(command, search)
            if (start < 0) break
            var cursor = start + command.length
            val args = mutableListOf<String>()
            var valid = true
            for (index in 0 until arity) {
                cursor = skipSpaces(value, cursor)
                val group = readGroup(value, cursor)
                if (group == null) {
                    valid = false
                    break
                }
                args += group.first
                cursor = group.second
            }
            if (!valid) {
                search = start + command.length
                continue
            }
            val replacement = render(args)
            value = value.replaceRange(start, cursor, replacement)
            search = start + replacement.length
        }
        return value
    }

    private fun readGroup(source: String, start: Int): Pair<String, Int>? {
        var cursor = skipSpaces(source, start)
        if (cursor >= source.length) return null
        if (source[cursor] != '{') {
            val begin = cursor
            while (cursor < source.length && !source[cursor].isWhitespace() &&
                source[cursor] !in "{}()[]"
            ) {
                cursor += 1
            }
            return if (cursor > begin) source.substring(begin, cursor) to cursor else null
        }
        var depth = 1
        cursor += 1
        val begin = cursor
        while (cursor < source.length && depth > 0) {
            when (source[cursor]) {
                '{' -> depth += 1
                '}' -> depth -= 1
            }
            if (depth == 0) break
            cursor += 1
        }
        if (depth != 0) return null
        return source.substring(begin, cursor) to cursor + 1
    }

    private fun skipSpaces(source: String, start: Int): Int {
        var cursor = start
        while (cursor < source.length && source[cursor].isWhitespace()) cursor += 1
        return cursor
    }
}
