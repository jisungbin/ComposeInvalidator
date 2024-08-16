@file:Suppress("unused", "UnusedVariable")

package land.sungbin.composeinvestigator.compiler._source.lower.stateInitializer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable fun delegateStateVariable() {
  val state by remember { mutableStateOf(Unit) }
  var state2 by remember { run { mutableStateOf(Unit) } }
}