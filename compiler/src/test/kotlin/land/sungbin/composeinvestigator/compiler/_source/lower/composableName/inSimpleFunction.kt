@file:Suppress("unused")

package land.sungbin.composeinvestigator.compiler._source.lower.composableName

import androidx.compose.runtime.Composable
import land.sungbin.composeinvestigator.runtime.ComposableName
import land.sungbin.composeinvestigator.runtime.currentComposableInvalidationTracer

private val table by lazy { currentComposableInvalidationTracer }

@Composable private fun InSimpleFunction() {
  table.currentComposableName

  table.currentComposableName = ComposableName("InSimpleFunctionName")
  table.currentComposableName
}
