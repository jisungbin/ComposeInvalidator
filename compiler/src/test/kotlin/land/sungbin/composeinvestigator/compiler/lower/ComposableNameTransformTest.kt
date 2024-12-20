// Copyright 2024 Ji Sungbin
// SPDX-License-Identifier: Apache-2.0
package land.sungbin.composeinvestigator.compiler.lower

import kotlin.test.Ignore
import kotlin.test.Test
import land.sungbin.composeinvestigator.compiler.FeatureFlag
import land.sungbin.composeinvestigator.compiler._compilation.AbstractCompilerTest
import org.jetbrains.kotlin.utils.addToStdlib.enumSetOf

class ComposableNameTransformTest : AbstractCompilerTest(
  enumSetOf(FeatureFlag.InvalidationTraceTableIntrinsicCall),
  sourceRoot = "lower/composableName",
) {
  @Test fun inDefaultArgument() = diff(source("inDefaultArgument.kt"), contextSize = 0) {
    ""
  }

  @Ignore("TODO")
  @Test fun inFunctionalArgument() = diff(source("inFunctionalArgument.kt"), contextSize = 0) {
    ""
  }

  @Ignore("TODO")
  @Test fun inSimpleFunction() = diff(source("inSimpleFunction.kt"), contextSize = 0) {
    ""
  }
}
