/*
 * Designed and developed by Ji Sungbin 2023.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler.internal.tracker.table

import land.sungbin.composeinvestigator.compiler.internal.COMPOSABLE_INVALIDATION_TRACK_TABLE_CURRENT_COMPOSABLE_KEY_NAME_FQN_GETTER_INTRINSIC
import land.sungbin.composeinvestigator.compiler.internal.COMPOSABLE_INVALIDATION_TRACK_TABLE_CURRENT_COMPOSABLE_NAME_FQN_GETTER_INTRINSIC
import land.sungbin.composeinvestigator.compiler.internal.COMPOSABLE_INVALIDATION_TRACK_TABLE_CURRENT_COMPOSABLE_NAME_FQN_SETTER_INTRINSIC
import land.sungbin.composeinvestigator.compiler.internal.COMPOSABLE_INVALIDATION_TRACK_TABLE_FQN
import land.sungbin.composeinvestigator.compiler.internal.COMPOSABLE_NAME_FQN
import land.sungbin.composeinvestigator.compiler.internal.CURRENT_COMPOSABLE_INVALIDATION_TRACKER_FQN_GETTER_INTRINSIC
import land.sungbin.composeinvestigator.compiler.internal.irBoolean
import land.sungbin.composeinvestigator.compiler.internal.irString
import land.sungbin.composeinvestigator.compiler.internal.tracker.key.DurableWritableSlices
import land.sungbin.composeinvestigator.compiler.internal.tracker.key.irTracee
import land.sungbin.composeinvestigator.compiler.util.VerboseLogger
import land.sungbin.fastlist.fastLastOrNull
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private var composableNameSymbol: IrClassSymbol? = null

internal class InvalidationTrackTableCallTransformer(
  private val context: IrPluginContext,
  private val table: IrInvalidationTrackTable,
  @Suppress("unused") private val logger: VerboseLogger,
) : IrElementTransformerVoidWithContext(), IrPluginContext by context {
  init {
    if (composableNameSymbol == null) {
      composableNameSymbol = context.referenceClass(ClassId.topLevel(COMPOSABLE_NAME_FQN))
    }
  }

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
    withinScope(declaration) { declaration.body?.transformChildrenVoid() }
    return super.visitSimpleFunction(declaration)
  }

  override fun visitCall(expression: IrCall): IrExpression {
    val callFqName = expression.symbol.owner.kotlinFqName
    val callParentFqName = expression.symbol.owner.parent.kotlinFqName
    return when {
      callFqName == CURRENT_COMPOSABLE_INVALIDATION_TRACKER_FQN_GETTER_INTRINSIC -> {
        table.propGetter()
      }
      callFqName == COMPOSABLE_INVALIDATION_TRACK_TABLE_CURRENT_COMPOSABLE_NAME_FQN_GETTER_INTRINSIC &&
        callParentFqName == COMPOSABLE_INVALIDATION_TRACK_TABLE_FQN -> {
        val name = when (val function = lastReachedFunction()) {
          null -> "<unknown>"
          else -> irTracee[DurableWritableSlices.DURABLE_FUNCTION_KEY, function]?.userProvideName ?: function.name.asString()
        }

        IrConstructorCallImpl.fromSymbolOwner(
          type = composableNameSymbol!!.defaultType,
          constructorSymbol = composableNameSymbol!!.constructors.single(),
        ).apply {
          putValueArgument(0, irString(name))
        }
      }
      callFqName == COMPOSABLE_INVALIDATION_TRACK_TABLE_CURRENT_COMPOSABLE_NAME_FQN_SETTER_INTRINSIC &&
        callParentFqName == COMPOSABLE_INVALIDATION_TRACK_TABLE_FQN -> {
        val function = lastReachedFunction()
        var result = false

        if (function != null) {
          val userProvideName =
            expression
              .getValueArgument(0).cast<IrConstructorCall>()
              .getValueArgument(0).safeAs<IrConst<String>>()?.value
              ?: error("Currently, only string hardcodes are supported as arguments to ComposableName. (${expression.dumpKotlinLike()})")
          val prevKey = irTracee[DurableWritableSlices.DURABLE_FUNCTION_KEY, function]!!
          val newKey = prevKey.copy(userProvideName = userProvideName)

          irTracee[DurableWritableSlices.DURABLE_FUNCTION_KEY, function] = newKey
          result = true
        }

        irBoolean(result)
      }
      callFqName == COMPOSABLE_INVALIDATION_TRACK_TABLE_CURRENT_COMPOSABLE_KEY_NAME_FQN_GETTER_INTRINSIC &&
        callParentFqName == COMPOSABLE_INVALIDATION_TRACK_TABLE_FQN -> {
        val function = lastReachedFunction()
        if (function != null) {
          val key = irTracee[DurableWritableSlices.DURABLE_FUNCTION_KEY, function]!!
          irString(key.keyName)
        } else {
          irString("<unknown>")
        }
      }
      else -> super.visitCall(expression)
    }
  }

  private fun lastReachedFunction(): IrSimpleFunction? =
    allScopes
      .fastLastOrNull { scope -> scope.irElement is IrSimpleFunction }
      ?.irElement
      ?.cast()
}
