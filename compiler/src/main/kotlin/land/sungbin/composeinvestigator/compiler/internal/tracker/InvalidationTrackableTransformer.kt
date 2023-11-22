/*
 * Designed and developed by Ji Sungbin 2023.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler.internal.tracker

import androidx.compose.compiler.plugins.kotlin.analysis.StabilityInferencer
import androidx.compose.compiler.plugins.kotlin.analysis.normalize
import land.sungbin.composeinvestigator.compiler.internal.COMPOSABLE_FQN
import land.sungbin.composeinvestigator.compiler.internal.COMPOSER_FQN
import land.sungbin.composeinvestigator.compiler.internal.COMPOSER_KT_FQN
import land.sungbin.composeinvestigator.compiler.internal.IS_TRACE_IN_PROGRESS
import land.sungbin.composeinvestigator.compiler.internal.SKIP_TO_GROUP_END
import land.sungbin.composeinvestigator.compiler.internal.TRACE_EVENT_END
import land.sungbin.composeinvestigator.compiler.internal.TRACE_EVENT_START
import land.sungbin.composeinvestigator.compiler.internal.irString
import land.sungbin.composeinvestigator.compiler.internal.irTracee
import land.sungbin.composeinvestigator.compiler.internal.logger.InvestigateLogger
import land.sungbin.composeinvestigator.compiler.internal.origin.InvalidationTrackerOrigin
import land.sungbin.composeinvestigator.compiler.internal.stability.toIrDeclarationStability
import land.sungbin.composeinvestigator.compiler.internal.tracker.key.DurableWritableSlices
import land.sungbin.composeinvestigator.compiler.util.VerboseLogger
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.addElement
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.load.kotlin.FacadeClassSource
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.cast

internal class InvalidationTrackableTransformer(
  private val context: IrPluginContext,
  private val logger: VerboseLogger,
  private val stabilityInferencer: StabilityInferencer,
) : AbstractInvalidationTrackingLower(context), IrPluginContext by context {
  private class IrSymbolOwnerWithData<D>(private val owner: IrSymbolOwner, val data: D) : IrSymbolOwner by owner

  private val currentInvalidationTrackTable: IrInvalidationTrackTable?
    get() =
      allScopes
        .lastOrNull { scope ->
          val element = scope.irElement
          element is IrSymbolOwnerWithData<*> && element.data is IrInvalidationTrackTable
        }
        ?.irElement
        ?.cast<IrSymbolOwnerWithData<IrInvalidationTrackTable>>()
        ?.data

  override fun visitFileNew(declaration: IrFile): IrFile {
    val invalidationTrackTable = IrInvalidationTrackTable.create(context, declaration)
    declaration.addChild(invalidationTrackTable.prop)
    logger("visitNewFile dump: ${declaration.dump()}")
    logger("visitNewFile dumpKotlinLike: ${declaration.dumpKotlinLike()}")
    return withinScope(IrSymbolOwnerWithData(declaration, invalidationTrackTable)) {
      super.visitFileNew(declaration)
    }
  }

  override fun visitFunctionNew(declaration: IrFunction): IrStatement {
    if (!declaration.hasAnnotation(COMPOSABLE_FQN)) return super.visitFunctionNew(declaration)
    declaration.body?.transformChildrenVoid()
    return super.visitFunctionNew(declaration)
  }

  // <BLOCK> {
  //   <WHEN> when { <CALL> isTraceInProgress() -> <CALL> traceEventStart() }
  //   composable()
  //   <WHEN> when { <CALL> isTraceInProgress() -> <CALL> traceEventEnd() }
  // }
  override fun visitBlock(expression: IrBlock): IrExpression {
    // skip if it is already transformed
    if (expression.origin == InvalidationTrackerOrigin) return super.visitBlock(expression)

    // composable ir block is always has more than 3 statements
    if (expression.statements.size < 3) return super.visitBlock(expression)

    val firstStatement = expression.statements.first()
    val lastStatement = expression.statements.last()

    // composable ir block's first and last statement must be IrWhen
    if (firstStatement !is IrWhen || lastStatement !is IrWhen) return super.visitBlock(expression)

    val isComposerIrBlock = firstStatement.isComposerTrackBranch() && lastStatement.isComposerTrackBranch()
    if (!isComposerIrBlock) return super.visitBlock(expression)

    val newStatements = mutableListOf<IrStatement>()
    val currentInvalidationTrackTable = currentInvalidationTrackTable!!

    val paramInfoType = currentInvalidationTrackTable.paramInfoSymbol.owner.defaultType
    val paramInfoGenericTypeProjection = makeTypeProjection(type = paramInfoType, variance = Variance.OUT_VARIANCE)
    val paramInfoGenericType = irBuiltIns.arrayClass.typeWithArguments(listOf(paramInfoGenericTypeProjection))
    val paramInfos = IrVarargImpl(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      type = paramInfoGenericType,
      varargElementType = paramInfoType,
    )

    // The last two arguments are generated by the Compose compiler ($composer, $changed)
    val validValueParamters = currentFunctionOrNull?.valueParameters?.dropLast(2).orEmpty()
    for (param in validValueParamters) {
      fun nameHint(fieldName: String) = "${param.name.asString()}\$$fieldName"
      val name = irTmpVariableInCurrentFun(irString(param.name.asString()), nameHint = nameHint("name"))
      val valueGetter = irGetValue(param)
      val valueString = irTmpVariableInCurrentFun(irToString(valueGetter), nameHint = nameHint("value#toString"))
      val hashCode = irTmpVariableInCurrentFun(irHashCode(valueGetter), nameHint = nameHint("value#hashCode"))
      val stability = irTmpVariableInCurrentFun(
        stabilityInferencer.stabilityOf(valueGetter).normalize().toIrDeclarationStability(context),
        nameHint = nameHint("value#stability"),
      )

      newStatements += listOf(name, valueString, hashCode, stability)
      paramInfos.addElement(
        currentInvalidationTrackTable.obtainParameterInfo(
          name = name,
          valueString = valueString,
          valueHashCode = hashCode,
          stability = stability,
        ),
      )
    }

    val currentFunctionKeyName =
      irTracee[DurableWritableSlices.DURABLE_FUNCTION_KEY, currentFunctionOrNull!! as IrAttributeContainer]!!.name
    val computeDiffParamsIfPresent =
      currentInvalidationTrackTable.irComputeDiffParamsIfPresent(
        keyName = irString(currentFunctionKeyName),
        originalName = irString(currentFunctionName),
        paramInfos = paramInfos,
      )
    val computeDiffParamsIfPresentVariable = irTmpVariableInCurrentFun(
      computeDiffParamsIfPresent,
      nameHint = "$currentFunctionName\$diffParams",
    )
    newStatements += computeDiffParamsIfPresentVariable

    val originalLogMessage = irString("[INVALIDATION_TRACKER] <${getCurrentFunctionNameIntercepttedAnonymous()}> invalidation processed")
    val affectedComposableSymbol = InvestigateLogger.obtainAffectedComposableSymbol(context)
    val affectedComposableCall = IrConstructorCallImpl.fromSymbolOwner(
      type = affectedComposableSymbol.owner.defaultType,
      constructorSymbol = affectedComposableSymbol.constructors.single(),
    ).apply {
      putValueArgument(0, irString(currentFunctionName))
      putValueArgument(1, irString(getCurrentFunctionPackage()))
    }
    val logTypeSymbol = InvestigateLogger.obtainLogTypeSymbol(context)
    val logTypeInvalidationProcessedSymbol = InvestigateLogger.obtainLogTypeInvalidationProcessedSymbol(context)
    val logTypeCall = IrConstructorCallImpl.fromSymbolOwner(
      type = logTypeSymbol.owner.defaultType,
      constructorSymbol = logTypeInvalidationProcessedSymbol.constructors.single(),
    ).apply {
      putValueArgument(0, irGetValue(computeDiffParamsIfPresentVariable))
    }
    val loggerCall = InvestigateLogger.makeIrCall(
      composable = affectedComposableCall,
      logType = logTypeCall,
      originalMessage = originalLogMessage,
    )
    newStatements += loggerCall

    expression.statements.addAll(1, newStatements)
    expression.origin = InvalidationTrackerOrigin

    val log = buildString {
      for ((index, statement) in expression.statements.withIndex()) {
        val dump = run {
          val dump = statement.dump().trimIndent()
          if (dump.length > 500) dump.substring(0, 500) + "..." else dump
        }
        if (index == 1) appendLine(">>>>>>>>>> ADD: ${loggerCall.dump()}")
        appendLine(dump)
        if (index == 3) {
          appendLine("...")
          break
        }
      }
    }

    logger("[invalidation processed] $log")
    logger("[invalidation processed] dump: ${expression.dump()}")
    logger("[invalidation processed] dumpKotlinLike: ${expression.dumpKotlinLike()}")

    return super.visitBlock(expression)
  }

  // <WHEN> when {
  //   ...
  //   else -> <CALL> $composer.skipToGroupEnd()
  // }
  override fun visitElseBranch(branch: IrElseBranch): IrElseBranch {
    val call = branch.result as? IrCall ?: return super.visitElseBranch(branch)

    val fnName = call.symbol.owner.name
    val fnParentFqn = call.symbol.owner.parent.kotlinFqName

    // SKIP_TO_GROUP_END is declared in 'androidx.compose.runtime.Composer'
    if (fnName == SKIP_TO_GROUP_END && fnParentFqn == COMPOSER_FQN) {
      val originalLogMessage = irString("[INVALIDATION_TRACKER] <${getCurrentFunctionNameIntercepttedAnonymous()}> invalidation skipped")
      val affectedComposableSymbol = InvestigateLogger.obtainAffectedComposableSymbol(context)
      val affectedComposableCall = IrConstructorCallImpl.fromSymbolOwner(
        type = affectedComposableSymbol.owner.defaultType,
        constructorSymbol = affectedComposableSymbol.constructors.single(),
      ).apply {
        putValueArgument(0, irString(currentFunctionName))
        putValueArgument(1, irString(getCurrentFunctionPackage()))
      }
      val logTypeSymbol = InvestigateLogger.obtainLogTypeSymbol(context)
      val logTypeInvalidationSkippedSymbol = InvestigateLogger.obtainLogTypeInvalidationSkippedSymbol(context)
      val logTypeCall = IrGetObjectValueImpl(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = logTypeSymbol.owner.defaultType,
        symbol = logTypeInvalidationSkippedSymbol,
      )
      val loggerCall = InvestigateLogger.makeIrCall(
        composable = affectedComposableCall,
        logType = logTypeCall,
        originalMessage = originalLogMessage,
      )

      val block = IrBlockImpl(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = irBuiltIns.unitType,
        origin = InvalidationTrackerOrigin,
        statements = listOf(loggerCall, call),
      )

      branch.result = block
      logger("[invalidation skipped] transformed: ${call.dumpKotlinLike()} -> ${block.dumpKotlinLike()}")
    }

    return super.visitElseBranch(branch)
  }

  private fun IrWhen.isComposerTrackBranch(): Boolean {
    val branch = branches.singleOrNull() ?: return false
    val `if` = (branch.condition as? IrCall ?: return false).symbol.owner
    val then = (branch.result as? IrCall ?: return false).symbol.owner

    // IS_TRACE_IN_PROGRESS, TRACE_EVENT_START, TRACE_EVENT_END are declared in 'androidx.compose.runtime.ComposerKt' (top-level)
    if (!`if`.isTopLevel || !then.isTopLevel) return false

    val thenName = then.name
    val thenParentFqn = then.unsafeGetTopLevelParentFqn()

    val validIf = `if`.name == IS_TRACE_IN_PROGRESS && `if`.unsafeGetTopLevelParentFqn() == COMPOSER_KT_FQN
    val validThen = (thenName == TRACE_EVENT_START || thenName == TRACE_EVENT_END) && thenParentFqn == COMPOSER_KT_FQN

    return validIf && validThen
  }
}

// TODO(multiplatform): this is jvm specific implementation
private fun IrFunction.unsafeGetTopLevelParentFqn(): FqName =
  parent.cast<IrClass>()
    .source.cast<FacadeClassSource>()
    .className.getFqNameForClassNameWithoutDollars() // JvmClassName -> FqName
