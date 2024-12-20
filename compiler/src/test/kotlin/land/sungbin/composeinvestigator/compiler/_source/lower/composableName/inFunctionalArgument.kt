@file:Suppress("unused")

package land.sungbin.composeinvestigator.compiler._source.lower.composableName;

import androidx.compose.runtime.Composable
import land.sungbin.composeinvestigator.runtime.ComposableName
import land.sungbin.composeinvestigator.runtime.currentComposableInvalidationTracer

private val table by lazy { currentComposableInvalidationTracer }

@Composable private fun InFunctionalArgument() {
  table.currentComposableName

  lambda {
    table.currentComposableName

    table.currentComposableName = ComposableName("InFunctionalArgumentName")
    table.currentComposableName
  }

  table.currentComposableName

  table.currentComposableName = ComposableName("InBody")
  table.currentComposableName
}

@Composable private fun lambda(content: @Composable () -> Unit) = Unit