package io.github.mangi.eta.ui.insets

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeImeInsetsTest {
    private class FixedInsets(
        private val bottom: Int,
        private val top: Int = 0,
    ) : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0

        override fun getTop(density: Density): Int = top

        override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0

        override fun getBottom(density: Density): Int = bottom
    }

    private val density = Density(1f)

    @Test
    fun followsAnimatedInsetWhileHealthy() {
        val insets = SafeImeInsets(animated = FixedInsets(bottom = 720), target = FixedInsets(bottom = 0))

        assertEquals(720, insets.getBottom(density))
    }

    @Test
    fun usesFallbackAnimationAfterTheAnimatedReadingIsStranded() {
        val insets = SafeImeInsets(animated = FixedInsets(bottom = 720), target = FixedInsets(bottom = 0))
        insets.isStranded = true
        insets.setFallbackBottom(360)

        assertEquals(360, insets.getBottom(density))
    }

    @Test
    fun usesTargetForNonBottomEdgesAfterStranding() {
        val insets = SafeImeInsets(
            animated = FixedInsets(bottom = 720, top = 90),
            target = FixedInsets(bottom = 0, top = 24),
        )
        insets.isStranded = true

        assertEquals(24, insets.getTop(density))
    }
}
