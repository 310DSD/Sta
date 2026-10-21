package io.github.mangi.eta.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexSymbolsTest {
    @Test
    fun convertsCommonScientificCommands() {
        assertEquals("α ≤ β ∈ ℝ", LatexSymbols.normalize("\\alpha \\leq \\beta \\in \\mathbb{R}"))
        assertEquals("∫₀² e⁻ˣ dx", LatexSymbols.normalize("\\int_{0}^{2} e^{-x} dx"))
        assertEquals("√(a²+b²)", LatexSymbols.normalize("\\sqrt{a^{2}+b^{2}}"))
        assertEquals("(a)/(b)", LatexSymbols.normalize("\\frac{a}{b}"))
    }

    @Test
    fun leavesUnknownCommandsVisible() {
        assertEquals("\\unknown{x}", LatexSymbols.normalize("\\unknown{x}"))
    }
}
