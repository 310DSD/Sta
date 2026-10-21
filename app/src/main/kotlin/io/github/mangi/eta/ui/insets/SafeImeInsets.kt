package io.github.mangi.eta.ui.insets

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.WeakHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Length of one quiet window used to decide that the animated IME inset has stopped following
 * the target. Two consecutive windows are required (128ms total).
 *
 * A healthy IME animation emits a new inset every frame; the gap that matters is only the dead
 * time between the target flipping and the animation's first progress callback (measured at
 * 17-36ms on a Pixel 8 in the upstream report). One 64ms window clears that with ~2x headroom;
 * the second window protects against a single slow frame while bounding the true-wedge delay.
 */
internal const val IME_STRAND_GRACE_MS = 64L

/** Duration of the local fallback animation once Compose's animated IME inset is proven stale. */
internal const val IME_FALLBACK_DURATION_MS = 240

/**
 * The IME inset to lay out against, in place of [WindowInsets.ime].
 *
 * Compose keeps a single `InsetsListener` per window. It sets an internal `runningAnimation`
 * flag in `onPrepare` and clears it only in `onEnd`; while the flag is set, `onApplyWindowInsets`
 * deliberately skips updating the animated inset. A cancelled IME animation (for example when a
 * predictive-back gesture races the IME close animation on Android 17) never delivers `onEnd`,
 * so `WindowInsets.ime` can freeze for the lifetime of the activity. `imePadding()` then keeps
 * applying the last keyboard height and the input bar no longer follows the keyboard.
 *
 * `WindowInsets.imeAnimationTarget` is updated before that flag is consulted, so it keeps
 * reporting the truth. This wrapper watches both readings: while they agree, it returns the
 * normal animated inset (perfectly synchronized with the system). When they disagree and stop
 * moving, it switches to a local 240ms animation towards the target, which restores the
 * input bar's rise/fall animation instead of snapping or staying frozen.
 *
 * Upstream: b/552500419, regression in androidx.compose.foundation:foundation-layout 1.4.0+.
 */
@Stable
class SafeImeInsets internal constructor(
    private val animated: WindowInsets,
    private val target: WindowInsets,
) : WindowInsets {
    var isStranded by mutableStateOf(false)
        internal set

    internal var fallbackBottom by mutableIntStateOf(0)
        private set

    internal fun setFallbackBottom(value: Int) {
        fallbackBottom = value
    }

    internal fun sample(density: Density): ImeInsetSample =
        ImeInsetSample(
            animated = animated.getBottom(density),
            target = target.getBottom(density),
        )

    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
        if (isStranded) target.getLeft(density, layoutDirection) else animated.getLeft(density, layoutDirection)

    override fun getTop(density: Density): Int =
        if (isStranded) target.getTop(density) else animated.getTop(density)

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
        if (isStranded) target.getRight(density, layoutDirection) else animated.getRight(density, layoutDirection)

    override fun getBottom(density: Density): Int =
        if (isStranded) fallbackBottom else animated.getBottom(density)

    internal companion object {
        private val perView = WeakHashMap<View, SafeImeInsets>()

        fun forView(view: View, animated: WindowInsets, target: WindowInsets): SafeImeInsets =
            synchronized(perView) {
                perView.getOrPut(view) { SafeImeInsets(animated, target) }
            }
    }
}

internal data class ImeInsetSample(
    val animated: Int,
    val target: Int,
)

/**
 * Drop-in replacement for `Modifier.imePadding()` that survives a stranded Compose IME inset.
 * Consumption semantics are identical: this is [windowInsetsPadding] over the safe inset.
 */
@Composable
fun Modifier.imePaddingSafe(): Modifier = windowInsetsPadding(rememberSafeImeInsets())

/**
 * Whether the keyboard is currently shown or animating towards shown.
 *
 * Reads the animation target rather than [WindowInsets.ime] because the latter can be frozen by
 * the Compose insets-listener defect described on [SafeImeInsets].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberImeVisible(): Boolean {
    val density = LocalDensity.current
    return WindowInsets.imeAnimationTarget.getBottom(density) > 0
}

/**
 * Watch a stream of IME inset samples and report when the animated value has stopped following
 * the target. Extracted from [rememberSafeImeInsets] so the timing/cancellation semantics can be
 * tested with virtual time.
 *
 * - A healthy animation emits a new sample every frame; `collectLatest` cancels the grace delay.
 * - A true wedge emits nothing, so the sample survives two grace windows and is reported once.
 * - If the animated value changes while stranded, Compose's listener has come back to life
 *   (e.g. a later successful animation delivered `onProgress`/`onEnd`, or the holder reset), so
 *   the consumer must return to the system-synchronized path.
 */
internal suspend fun watchForStrandedIme(
    samples: Flow<ImeInsetSample>,
    graceMillis: Long = IME_STRAND_GRACE_MS,
    onHealthy: suspend (ImeInsetSample) -> Unit = {},
    onStranded: suspend (ImeInsetSample) -> Unit,
) {
    var strandedAt: Int? = null
    samples.collectLatest { sample ->
        val strandedAnimated = strandedAt
        if (strandedAnimated == null) {
            if (sample.animated == sample.target) {
                onHealthy(sample)
                return@collectLatest
            }
            // Two consecutive quiet windows avoid stranding on a slow first frame while keeping
            // the true-wedge latency bounded (2 × 64ms) below a visible lag.
            delay(graceMillis)
            delay(graceMillis)
            strandedAt = sample.animated
            onStranded(sample)
        } else if (sample.animated != strandedAnimated) {
            strandedAt = null
            onHealthy(sample)
        } else {
            onStranded(sample)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberSafeImeInsets(): WindowInsets {
    val view = LocalView.current
    val density = LocalDensity.current
    val animated = WindowInsets.ime
    val target = WindowInsets.imeAnimationTarget
    val insets = remember(view) { SafeImeInsets.forView(view, animated, target) }

    LaunchedEffect(insets, density) {
        val fallback = Animatable(insets.fallbackBottom.toFloat())
        watchForStrandedIme(
            samples = snapshotFlow { insets.sample(density) },
            onHealthy = { sample ->
                val wasStranded = insets.isStranded
                insets.isStranded = false
                if (wasStranded) {
                    insets.setFallbackBottom(sample.animated)
                    fallback.snapTo(sample.animated.toFloat())
                }
            },
            onStranded = { sample ->
                if (!insets.isStranded) {
                    insets.setFallbackBottom(sample.animated)
                    fallback.snapTo(sample.animated.toFloat())
                    insets.isStranded = true
                }
                if (fallback.value.roundToInt() != sample.target) {
                    fallback.animateTo(
                        targetValue = sample.target.toFloat(),
                        animationSpec = tween(
                            durationMillis = IME_FALLBACK_DURATION_MS,
                            easing = FastOutSlowInEasing,
                        ),
                    ) {
                        insets.setFallbackBottom(value.roundToInt())
                    }
                    insets.setFallbackBottom(sample.target)
                }
            },
        )
    }

    return insets
}
