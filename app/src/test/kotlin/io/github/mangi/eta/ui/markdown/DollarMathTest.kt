package io.github.mangi.eta.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class DollarMathTest {
    @Test
    fun normalizesInlineDollarMath() {
        assertEquals("G", DollarMath.normalize("\$G\$"))
        assertEquals("H(0)=(3)/(0.1)=30", DollarMath.normalize("\$H(0)=\\frac{3}{0.1}=30\$"))
        assertEquals("K_v=limₛ→₀", DollarMath.normalize("\$K_v=\\lim_{s\\to 0}\$"))
        assertEquals(
            "(1.5t_p√(1-ζ²))/(π)",
            DollarMath.normalize("\$\\frac{1.5t_p\\sqrt{1-\\zeta^2}}{\\pi}\$"),
        )
        assertEquals("(a)/(b)", DollarMath.normalize("\$\\dfrac{a}{b}\$"))
    }

    @Test
    fun preservesCurrencyText() {
        assertEquals("价格 \$4 和 \$20 每百万", DollarMath.normalize("价格 \$4 和 \$20 每百万"))
    }
}
