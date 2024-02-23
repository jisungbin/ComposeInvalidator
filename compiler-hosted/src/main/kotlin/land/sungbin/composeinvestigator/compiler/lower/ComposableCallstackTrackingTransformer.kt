/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler.lower

import land.sungbin.composeinvestigator.compiler.VerboseLogger
import land.sungbin.composeinvestigator.compiler.origin.ComposableCallstackTrackerOrigin
import land.sungbin.composeinvestigator.compiler.util.irString
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.FqName

internal class ComposableCallstackTrackingTransformer(
  private val context: IrPluginContext,
  private val logger: VerboseLogger,
) : AbstractComposableCallstackTrackLower(context, logger), IrPluginContext by context {
  override fun transformComposableCall(parent: FqName, expression: IrCall): IrExpression =
    expression.wrapTryFinally(
      tryResult = IrBlockImpl(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = expression.type,
        origin = ComposableCallstackTrackerOrigin,
        statements = listOf(
          tracker.irPush(irString(parent.asString())),
          expression, // TODO: remove offsets
        ),
      ),
      finallyBlock = tracker.irPop(),
    ).also {
      logger("[ComposableCall] parent: ${parent.asString()}, expression: ${expression.render()}")
    }
}
