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
    ).sortedByDescending { it.first.length }

    private val fraction = Regex("""\\frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")
    private val sqrt = Regex("""\\sqrt\s*\{([^{}]*)\}""")
    private val textCommand = Regex("""\\(?:text|mathrm|mathbf|mathit|operatorname)\s*\{([^{}]*)\}""")
    private val spacing = Regex("""\\(?:left|right|,|;|!|quad|qquad)""")
    private val superscript = Regex("""\^\{([^{}]*)\}|\^([0-9+\-=()n])""")
    private val subscript = Regex("""_\{([^{}]*)\}|_([0-9+\-=()n])""")
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
        var value = textCommand.replace(raw) { it.groupValues[1] }
        value = superscript.replace(value) { m ->
            val body = m.groupValues[1].ifEmpty { m.groupValues[2] }
            body.map { superscripts[it] ?: it }.joinToString("")
        }
        value = subscript.replace(value) { m ->
            val body = m.groupValues[1].ifEmpty { m.groupValues[2] }
            body.map { subscripts[it] ?: it }.joinToString("")
        }
        value = fraction.replace(value) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
        value = sqrt.replace(value) { "√(${it.groupValues[1]})" }
        value = spacing.replace(value, "")
        commands.forEach { (command, symbol) -> value = value.replace(command, symbol) }
        return value.trim()
    }
}
