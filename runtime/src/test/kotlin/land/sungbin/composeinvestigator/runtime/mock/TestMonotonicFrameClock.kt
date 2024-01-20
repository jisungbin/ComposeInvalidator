/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package land.sungbin.composeinvestigator.runtime.mock

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestCoroutineScheduler

private const val DefaultFrameDelay = 16_000_000L

/**
 * Construct a [TestMonotonicFrameClock] for [coroutineScope], obtaining the [TestCoroutineScheduler]
 * from the scope's [context][CoroutineScope.coroutineContext]. This frame clock may be used to
 * consistently drive time under controlled tests.
 *
 * Calls to [TestMonotonicFrameClock.withFrameNanos] will schedule an upcoming frame
 * [frameDelayNanos] nanoseconds in the future by launching into [coroutineScope] if such a frame
 * has not yet been scheduled.
 */
@Suppress("TestFunctionName")
fun TestMonotonicFrameClock(
  coroutineScope: CoroutineScope,
  frameDelayNanos: Long = DefaultFrameDelay,
): TestMonotonicFrameClock = TestMonotonicFrameClock(
  coroutineScope = coroutineScope,
  testCoroutineScheduler = requireNotNull(coroutineScope.coroutineContext[TestCoroutineScheduler]) {
    "coroutuineScope should have TestCoroutineScheduler"
  },
  frameDelayNanos = frameDelayNanos,
)

/**
 * A [MonotonicFrameClock] with a time source controlled by a `kotlinx-coroutines-test`
 * [TestCoroutineScheduler]. This frame clock may be used to consistently drive time under controlled
 * tests.
 *
 * Calls to [withFrameNanos] will schedule an upcoming frame [frameDelayNanos] nanoseconds in the
 * future by launching into [coroutineScope] if such a frame has not yet been scheduled. The
 * current frame time for [withFrameNanos] is provided by [TestCoroutineScheduler]. It is strongly
 * suggested that [coroutineScope] contain the test dispatcher controlled by [TestCoroutineScheduler].
 */
class TestMonotonicFrameClock(
  private val coroutineScope: CoroutineScope,
  private val testCoroutineScheduler: TestCoroutineScheduler,
  @get:Suppress("MethodNameUnits") // Nanos for high-precision animation clocks
  val frameDelayNanos: Long = DefaultFrameDelay,
) : MonotonicFrameClock {
  private val lock = Any()
  private val awaiters = mutableListOf<Awaiter<*>>()
  private var posted = false

  private class Awaiter<R>(
    private val onFrame: (Long) -> R,
    private val continuation: CancellableContinuation<R>,
  ) {
    fun runFrame(frameTimeNanos: Long): () -> Unit {
      val result = runCatching { onFrame(frameTimeNanos) }
      return { continuation.resumeWith(result) }
    }
  }

  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
    suspendCancellableCoroutine { co ->
      synchronized(lock) {
        awaiters.add(Awaiter(onFrame, co))
        maybeLaunchTickRunner()
      }
    }

  private fun maybeLaunchTickRunner() {
    if (!posted) {
      posted = true
      coroutineScope.launch {
        delay(frameDelayMillis)
        synchronized(lock) {
          posted = false
          val toRun = awaiters.toList()
          awaiters.clear()
          val frameTime = testCoroutineScheduler.currentTime * 1_000_000
          // In case of awaiters on an immediate dispatcher, run all frame callbacks
          // before resuming any associated continuations with the results.
          toRun.map { it.runFrame(frameTime) }.forEach { it() }
        }
      }
    }
  }
}

/**
 * The frame delay time for the [TestMonotonicFrameClock] in milliseconds.
 */
val TestMonotonicFrameClock.frameDelayMillis: Long
  get() = frameDelayNanos / 1_000_000
