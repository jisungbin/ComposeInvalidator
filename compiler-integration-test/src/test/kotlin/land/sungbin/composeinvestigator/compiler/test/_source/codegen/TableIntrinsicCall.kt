/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

@file:Suppress("TestFunctionName")

package land.sungbin.composeinvestigator.compiler.test._source.codegen

import androidx.compose.runtime.Composable
import land.sungbin.composeinvestigator.runtime.currentComposableInvalidationTracer

@Composable
@Suppress("unused")
private fun Variable() {
  val table = currentComposableInvalidationTracer

  println("Current Composable name is \"${table.currentComposableName.name}\".")
  println("Current Composable keyName is '${table.currentComposableKeyName}'.")
}

@Composable
@Suppress("unused")
private fun Direct() {
  currentComposableInvalidationTracer

  println("Current Composable name is \"${currentComposableInvalidationTracer.currentComposableName.name}\".")
  println("Current Composable keyName is '${currentComposableInvalidationTracer.currentComposableKeyName}'.")
}
