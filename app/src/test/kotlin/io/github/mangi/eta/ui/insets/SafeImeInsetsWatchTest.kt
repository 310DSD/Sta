package io.github.mangi.eta.ui.insets

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeImeInsetsWatchTest {
    private fun sample(animated: Int, target: Int) = ImeInsetSample(animated, target)

    @Test
    fun healthyFramesCancelTheStrandingWindow() = runTest {
        val events = mutableListOf<String>()
        val samples = flow {
            emit(sample(720, 0))
            kotlinx.coroutines.delay(30)
            emit(sample(600, 0))
            kotlinx.coroutines.delay(30)
            emit(sample(480, 0))
            kotlinx.coroutines.delay(30)
            emit(sample(0, 0))
        }

        val job = launch {
            watchForStrandedIme(
                samples = samples,
                graceMillis = 64,
                onHealthy = { events += "healthy" },
                onStranded = { events += "stranded" },
            )
        }
        advanceUntilIdle()

        assertEquals(listOf("healthy"), events)
        job.cancel()
    }

    @Test
    fun requiresTwoQuietWindowsBeforeStranding() = runTest {
        val events = mutableListOf<String>()
        val samples = flow {
            emit(sample(720, 0))
            kotlinx.coroutines.awaitCancellation()
        }
        val job = launch {
            watchForStrandedIme(
                samples = samples,
                graceMillis = 64,
                onStranded = { events += "stranded" },
            )
        }
        runCurrent()

        advanceTimeBy(70)
        assertTrue(events.isEmpty())

        advanceTimeBy(70)
        assertEquals(listOf("stranded"), events)
        job.cancel()
    }

    @Test
    fun aChangedAnimatedValueRecoversFromStranding() = runTest {
        val events = mutableListOf<String>()
        val samples = MutableSharedFlow<ImeInsetSample>(extraBufferCapacity = 4)
        val job = launch {
            watchForStrandedIme(
                samples = samples,
                graceMillis = 64,
                onHealthy = { events += "healthy" },
                onStranded = { events += "stranded" },
            )
        }
        runCurrent()

        samples.emit(sample(720, 0))
        advanceTimeBy(140)
        assertEquals(listOf("stranded"), events)

        // A later successful animation moves the animated reading; Compose's listener is alive.
        samples.emit(sample(360, 0))
        advanceUntilIdle()
        assertEquals(listOf("stranded", "healthy"), events)
        job.cancel()
    }
}
