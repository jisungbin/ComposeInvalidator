/*
 * Designed and developed by Ji Sungbin 2023.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler.internal.tracker.logger

import land.sungbin.composeinvestigator.compiler.internal.COMPOSABLE_INVALIDATION_TYPE_FQN
import land.sungbin.composeinvestigator.compiler.internal.COMPOSE_INVESTIGATOR_CONFIG_FQN
import land.sungbin.composeinvestigator.compiler.internal.ComposableInvalidationLogger_INVOKE
import land.sungbin.composeinvestigator.compiler.internal.ComposableInvalidationType_PROCESSED
import land.sungbin.composeinvestigator.compiler.internal.ComposableInvalidationType_SKIPPED
import land.sungbin.composeinvestigator.compiler.internal.ComposeInvestigatorConfig_INVALIDATION_LOGGER
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.name.ClassId

public object IrInvalidationLogger {
  private var loggerContainerSymbol: IrClassSymbol? = null
  private var loggerGetterSymbol: IrSimpleFunctionSymbol? = null
  private var loggerInvokerSymbol: IrSimpleFunctionSymbol? = null

  private var invalidationTypeSymbol: IrClassSymbol? = null
  private var invalidationTypeProcessedSymbol: IrClassSymbol? = null
  private var invalidationTypeSkippedSymbol: IrClassSymbol? = null

  public val irInvalidationTypeSymbol: IrClassSymbol get() = invalidationTypeSymbol!!

  public fun init(context: IrPluginContext) {
    loggerContainerSymbol = context.referenceClass(ClassId.topLevel(COMPOSE_INVESTIGATOR_CONFIG_FQN))!!
    loggerGetterSymbol = loggerContainerSymbol!!.getPropertyGetter(ComposeInvestigatorConfig_INVALIDATION_LOGGER.asString())!!
    loggerInvokerSymbol = loggerGetterSymbol!!.owner.returnType.classOrFail.getSimpleFunction(ComposableInvalidationLogger_INVOKE.asString())!!

    invalidationTypeSymbol = context.referenceClass(ClassId.topLevel(COMPOSABLE_INVALIDATION_TYPE_FQN))!!
    invalidationTypeProcessedSymbol = invalidationTypeSymbol!!.owner.sealedSubclasses.single { clz ->
      clz.owner.name == ComposableInvalidationType_PROCESSED
    }
    invalidationTypeSkippedSymbol = invalidationTypeSymbol!!.owner.sealedSubclasses.single { clz ->
      clz.owner.name == ComposableInvalidationType_SKIPPED
    }
  }

  public fun irLog(
    affectedComposable: IrDeclarationReference,
    invalidationType: IrDeclarationReference,
  ): IrCall = IrCallImpl.fromSymbolOwner(
    startOffset = UNDEFINED_OFFSET,
    endOffset = UNDEFINED_OFFSET,
    symbol = loggerInvokerSymbol!!,
  ).also { invokeCall ->
    invokeCall.dispatchReceiver = IrCallImpl.fromSymbolOwner(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      symbol = loggerGetterSymbol!!,
    ).also { loggerGetter ->
      loggerGetter.dispatchReceiver = IrGetObjectValueImpl(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = loggerContainerSymbol!!.defaultType,
        symbol = loggerContainerSymbol!!,
      )
    }
  }.apply {
    putValueArgument(0, affectedComposable)
    putValueArgument(1, invalidationType)
  }

  public fun irInvalidationTypeProcessed(reason: IrExpression): IrConstructorCall =
    IrConstructorCallImpl.fromSymbolOwner(
      type = invalidationTypeProcessedSymbol!!.defaultType,
      constructorSymbol = invalidationTypeProcessedSymbol!!.constructors.single(),
    ).apply {
      putValueArgument(0, reason)
    }

  public fun irInvalidationTypeSkipped(): IrGetObjectValue =
    IrGetObjectValueImpl(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      type = invalidationTypeSkippedSymbol!!.defaultType,
      symbol = invalidationTypeSkippedSymbol!!,
    )
}
