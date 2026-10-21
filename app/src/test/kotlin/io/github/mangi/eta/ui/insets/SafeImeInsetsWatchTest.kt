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

        // A later successful animation reaches the target again: healthy, no new strand.
        samples.emit(sample(0, 0))
        advanceUntilIdle()
        assertEquals(listOf("stranded", "healthy"), events)
        job.cancel()
    }

    @Test
    fun aChangedAnimatedValueThatStillMissesTargetRearmsTheWatchdog() = runTest {
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

        // One frame moved, but it stopped short of the target and no further frame arrives.
        samples.emit(sample(360, 0))
        advanceTimeBy(140)
        assertEquals(listOf("stranded", "healthy", "stranded"), events)
        job.cancel()
    }

    @Test
    fun aRestartedWatcherResumesStrandingWithoutAnotherGraceWindow() = runTest {
        val events = mutableListOf<String>()
        val job = launch {
            watchForStrandedIme(
                samples = flow {
                    emit(sample(0, 900))
                    kotlinx.coroutines.awaitCancellation()
                },
                graceMillis = 64,
                resumeStranded = true,
                onStranded = { events += "stranded" },
            )
        }
        runCurrent()
        assertEquals(listOf("stranded"), events)
        job.cancel()
    }

    @Test
    fun aSampleInsideTheGraceWindowRestartsBothWindows() = runTest {
        val events = mutableListOf<String>()
        val samples = MutableSharedFlow<ImeInsetSample>(extraBufferCapacity = 4)
        val job = launch {
            watchForStrandedIme(
                samples = samples,
                graceMillis = 64,
                onStranded = { events += "stranded" },
            )
        }
        runCurrent()

        samples.emit(sample(720, 0))
        advanceTimeBy(70)
        assertTrue(events.isEmpty())
        samples.emit(sample(700, 0))
        advanceTimeBy(70)
        assertTrue(events.isEmpty())
        advanceTimeBy(70)
        assertEquals(listOf("stranded"), events)
        job.cancel()
    }
}
