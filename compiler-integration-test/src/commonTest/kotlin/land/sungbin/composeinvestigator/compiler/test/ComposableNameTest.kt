// Copyright 2024 Ji Sungbin
// SPDX-License-Identifier: Apache-2.0
package land.sungbin.composeinvestigator.compiler.test

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.isEqualTo
import composemock.runComposeUnit
import kotlin.test.Test
import land.sungbin.composeinvestigator.runtime.ComposableName
import land.sungbin.composeinvestigator.runtime.currentComposableInvalidationTracer

class ComposableNameTest {
  @Test fun setComposableNameOnLambda() = runComposeUnit {
    val table = currentComposableInvalidationTracer

    assertThat(table.currentComposableName.name).isEqualTo("<Anonymous>")

    table.currentComposableName = ComposableName("SetComposableNameOnSimpleFunction")
    assertThat(table.currentComposableName.name).isEqualTo("SetComposableNameOnSimpleFunction")
  }

  @Test fun setComposableNameOnSimpleFunction() = runComposeUnit {
    MyComposableForComposableNameTest()
  }

  @Composable private fun MyComposableForComposableNameTest() {
    val table = currentComposableInvalidationTracer

    assertThat(table.currentComposableName.name).isEqualTo("MyComposableForComposableNameTest")

    table.currentComposableName = ComposableName("TestComposableName")
    assertThat(table.currentComposableName.name).isEqualTo("TestComposableName")
  }
}

@Suppress("unused")
@Composable private fun emptyComposableToInstantiateComposableInvalidationTraceTable() = Unit
