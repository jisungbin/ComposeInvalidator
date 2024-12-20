// Copyright 2024 Ji Sungbin
// SPDX-License-Identifier: Apache-2.0
@file:NoInvestigation

package composemock

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import land.sungbin.composeinvestigator.runtime.NoInvestigation

// Copyright 2020-2023 JetBrains s.r.o. and respective authors and developers.
// https://github.com/JetBrains/compose-multiplatform/blob/1b2669f59d8f9a5cda1a26388a51e8c6793981c8/compose/integrations/composable-test-cases/common/src/commonMain/kotlin/com/example/common/Applier.kt

// Modified by Ji Sungbin: code style changes and helper functions added.

open class StringsNode(
  var text: String? = null,
  val children: MutableList<StringsNode> = mutableListOf(),
) {
  override fun toString(): String =
    children.joinToString(prefix = "${text ?: "_"}:{", postfix = "}")
}

private class PlainTextNode(text: String? = null) : StringsNode(text) {
  override fun toString(): String = text.orEmpty()
}

private class StringsNodeApplier(root: StringsNode) : AbstractApplier<StringsNode>(root) {
  override fun insertBottomUp(index: Int, instance: StringsNode) {
    // Ignored. Use insertTopDown instead.
  }

  override fun insertTopDown(index: Int, instance: StringsNode) {
    current.children.add(index, instance)
  }

  override fun move(from: Int, to: Int, count: Int) {
    current.children.move(from, to, count)
  }

  override fun remove(index: Int, count: Int) {
    current.children.remove(index, count)
  }

  override fun onClear() {
    current.children.clear()
  }
}

private object GlobalSnapshotManager {
  private var removeWriteObserver: (ObserverHandle)? = null

  fun ensureStarted() {
    if (removeWriteObserver != null) removeWriteObserver!!.dispose()
    removeWriteObserver = Snapshot.registerGlobalWriteObserver(globalWriteObserver)
  }

  private val globalWriteObserver: (Any) -> Unit = {
    Snapshot.sendApplyNotifications()
  }
}

private class TestMonotonicClock : MonotonicFrameClock {
  private var now = 0L

  override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = suspendCoroutine { cont ->
    val now = ++now
    val currentNanos = now * MILLISECOND_NANO
    cont.resume(onFrame(currentNanos))
  }

  companion object {
    private const val MILLISECOND_NANO = 1_000_000
  }
}

@JvmInline value class RunningJob(val job: Job)

infix fun RunningJob.action(block: () -> Unit) {
  try {
    block()
  } finally {
    job.cancel()
  }
}

fun runComposeUnit(
  recomposerContext: CoroutineContext? = null,
  content: @Composable () -> Unit,
) {
  runCompose(recomposerContext, content)
}

fun runCompose(
  recomposerContext: CoroutineContext? = null,
  content: @Composable () -> Unit,
): StringsNode {
  GlobalSnapshotManager.ensureStarted()

  val clock = TestMonotonicClock()
  val context = if (recomposerContext != null) recomposerContext + clock else clock
  val recomposer = Recomposer(context)

  CoroutineScope(context).launch(start = CoroutineStart.UNDISPATCHED) {
    recomposer.runRecomposeAndApplyChanges()
  }

  val root = StringsNode("root")
  val composition = Composition(
    applier = StringsNodeApplier(root),
    parent = recomposer,
  )

  composition.setContent(content)
  return root
}

suspend inline fun runningCompose(noinline content: @Composable () -> Unit): RunningJob =
  RunningJob(Job()).also { running -> runCompose(coroutineContext + running.job, content) }

@Composable fun Text(text: String) {
  ComposeNode<StringsNode, StringsNodeApplier>(
    factory = { PlainTextNode(text) },
    update = { set(text) { value -> this.text = value } },
  )
}

@Composable fun Container(name: String? = null, content: @Composable () -> Unit) {
  ComposeNode<StringsNode, StringsNodeApplier>(
    factory = { StringsNode(name) },
    update = { set(name) { value -> this.text = value } },
    content = content,
  )
}
