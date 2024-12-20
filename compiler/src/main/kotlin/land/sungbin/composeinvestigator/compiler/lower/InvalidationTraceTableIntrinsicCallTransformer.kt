// Copyright 2024 Ji Sungbin
// SPDX-License-Identifier: Apache-2.0
package land.sungbin.composeinvestigator.compiler.lower

import androidx.compose.compiler.plugins.kotlin.irTrace
import land.sungbin.composeinvestigator.compiler.COMPOSABLE_INVALIDATION_TRACE_TABLE_FQN
import land.sungbin.composeinvestigator.compiler.COMPOSABLE_NAME_FQN
import land.sungbin.composeinvestigator.compiler.CURRENT_COMPOSABLE_INVALIDATION_TRACER_FQN
import land.sungbin.composeinvestigator.compiler.ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_KEY_NAME
import land.sungbin.composeinvestigator.compiler.ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_NAME
import land.sungbin.composeinvestigator.compiler.UNKNOWN_STRING
import land.sungbin.composeinvestigator.compiler.analysis.DurationWritableSlices
import land.sungbin.composeinvestigator.compiler.analysis.set
import land.sungbin.composeinvestigator.compiler.fromFqName
import land.sungbin.composeinvestigator.compiler.struct.IrComposableInformation
import land.sungbin.composeinvestigator.compiler.struct.IrInvalidationTraceTableHolder
import land.sungbin.composeinvestigator.compiler.struct.get
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyWithOffsets
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.utils.addToStdlib.cast

/**
 * Transforms the code to implement the use of the `ComposableInvalidationTraceTable`'s
 * Intrinsic API by utilizing metadata generated at compile time by `ComposeInvestigator`.
 */
public class InvalidationTraceTableIntrinsicCallTransformer(
  private val context: IrPluginContext,
  private val irComposableInformation: IrComposableInformation,
  private val tables: IrInvalidationTraceTableHolder,
) : IrElementTransformerVoidWithContext() {
  private val tableSymbol = context.referenceClass(ClassId.topLevel(COMPOSABLE_INVALIDATION_TRACE_TABLE_FQN))!!
  private val composableNameSymbol = context.referenceClass(ClassId.topLevel(COMPOSABLE_NAME_FQN))!!.owner

  private val currentTableGetterSymbol =
    context.referenceProperties(CallableId.fromFqName(CURRENT_COMPOSABLE_INVALIDATION_TRACER_FQN)).first().owner.getter!!

  private val currentComposableNameGetterSymbol =
    tableSymbol.getPropertyGetter(ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_NAME.asString())!!.owner

  private val currentComposableNameSetterSymbol =
    tableSymbol.getPropertySetter(ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_NAME.asString())!!.owner

  private val currentComposableKeyNameGetterSymbol =
    tableSymbol.getPropertyGetter(ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_KEY_NAME.asString())!!.owner

  override fun visitCall(expression: IrCall): IrExpression {
    // TODO Generating `throw Exception(NO_TABLE)` code to the target IR instead
    //  of throwing it here if no table exists
    val table by lazy { tables[currentFile] }
    return when (expression.symbol.owner.kotlinFqName) {
      currentTableGetterSymbol.kotlinFqName -> {
        table.propGetter(
          startOffset = expression.startOffset,
          endOffset = expression.endOffset,
        )
      }
      currentComposableNameGetterSymbol.kotlinFqName -> {
        val name = allScopes.lastComposable()?.let { composable ->
          IrComposableInformation.getName(context.irTrace[DurationWritableSlices.DURABLE_FUNCTION_KEY, composable]!!.composable)
            .copyWithOffsets(startOffset = expression.startOffset, endOffset = expression.endOffset)
        }
        val unknownName = context.irString(
          SpecialNames.UNKNOWN_STRING,
          startOffset = expression.startOffset,
          endOffset = expression.endOffset,
        )

        IrConstructorCallImpl.fromSymbolOwner(
          startOffset = expression.startOffset,
          endOffset = expression.endOffset,
          type = composableNameSymbol.defaultType,
          constructorSymbol = composableNameSymbol.symbol.constructors.first(),
        ).apply {
          putValueArgument(0, name ?: unknownName)
        }
      }
      currentComposableNameSetterSymbol.kotlinFqName -> {
        allScopes.lastComposable()?.let { composable ->
          val newName = expression
            .getValueArgument(0).cast<IrConstructorCall>()
            .getValueArgument(0).cast<IrConst>().value as String

          val originalKey = context.irTrace[DurationWritableSlices.DURABLE_FUNCTION_KEY, composable]!!
          val newComposable = irComposableInformation.copyFrom(originalKey.composable, name = context.irString(newName))

          context.irTrace[DurationWritableSlices.DURABLE_FUNCTION_KEY, composable] = originalKey.copy(composable = newComposable)
        }

        IrGetObjectValueImpl(
          startOffset = expression.startOffset,
          endOffset = expression.endOffset,
          type = context.irBuiltIns.unitClass.defaultType,
          symbol = context.irBuiltIns.unitClass.owner.symbol,
        )
      }
      currentComposableKeyNameGetterSymbol.kotlinFqName -> {
        allScopes.lastComposable()?.let { composable ->
          context.irString(
            context.irTrace[DurationWritableSlices.DURABLE_FUNCTION_KEY, composable]!!.keyName,
            startOffset = expression.startOffset,
            endOffset = expression.endOffset,
          )
        } ?: run {
          context.irString(
            SpecialNames.UNKNOWN_STRING,
            startOffset = expression.startOffset,
            endOffset = expression.endOffset,
          )
        }
      }
      else -> super.visitCall(expression)
    }
  }
}
