/*
 * Designed and developed by Ji Sungbin 2023.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler.internal.tracker

import androidx.compose.compiler.plugins.kotlin.analysis.StabilityInferencer
import androidx.compose.compiler.plugins.kotlin.analysis.normalize
import land.sungbin.composeinvestigator.compiler.internal.irInt
import land.sungbin.composeinvestigator.compiler.internal.irString
import land.sungbin.composeinvestigator.compiler.internal.origin.InvalidationTrackerOrigin
import land.sungbin.composeinvestigator.compiler.internal.stability.toIrDeclarationStability
import land.sungbin.composeinvestigator.compiler.internal.tracker.key.DurableWritableSlices
import land.sungbin.composeinvestigator.compiler.internal.tracker.key.irTracee
import land.sungbin.composeinvestigator.compiler.internal.tracker.logger.InvalidationLogger
import land.sungbin.composeinvestigator.compiler.util.VerboseLogger
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.addElement
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.types.Variance

internal class InvalidationTrackableTransformer(
  private val context: IrPluginContext,
  private val logger: VerboseLogger,
  private val stabilityInferencer: StabilityInferencer,
) : AbstractInvalidationTrackingLower(context, logger), IrPluginContext by context {
  override fun visitComposableBlock(function: IrSimpleFunction, expression: IrBlock): IrBlock {
    val newStatements = mutableListOf<IrStatement>()

    val currentKey = irTracee[DurableWritableSlices.DURABLE_FUNCTION_KEY, function]!!
    val currentUserProvideName = currentKey.userProvideName

    val currentFunctionName = currentUserProvideName ?: function.name.asString()
    val currentFunctionLocation = function.getSafelyLocation()
    val currentInvalidationTrackTable = currentInvalidationTrackTable!!

    val paramInfoType = InvalidationLogger.paramInfoSymbol!!.defaultType
    val paramInfoGenericTypeProjection = makeTypeProjection(type = paramInfoType, variance = Variance.OUT_VARIANCE)
    val paramInfoGenericType = irBuiltIns.arrayClass.typeWithArguments(listOf(paramInfoGenericTypeProjection))
    val paramInfos = IrVarargImpl(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      type = paramInfoGenericType,
      varargElementType = paramInfoType,
    )

    // The last two arguments are generated by the Compose compiler ($composer, $changed)
    val validValueParamters = function.valueParameters.dropLast(2)
    for (param in validValueParamters) {
      val name = irString(param.name.asString())
      val valueGetter = irGetValue(param)
      val valueString = irToString(valueGetter)
      val valueHashCode = irHashCode(valueGetter)
      val stability = stabilityInferencer.stabilityOf(valueGetter).normalize().toIrDeclarationStability(context)

      val paramInfo = InvalidationLogger.irParameterInfo(
        name = name,
        valueString = valueString,
        valueHashCode = valueHashCode,
        stability = stability,
      )
      val paramInfoVariable = irTmpVariableInCurrentFun(paramInfo, nameHint = "${param.name.asString()}\$paramInfo")

      newStatements += paramInfoVariable
      paramInfos.addElement(irGetValue(paramInfoVariable))
    }

    val computeInvalidationReason =
      currentInvalidationTrackTable.irComputeInvalidationReason(
        composableKeyName = irString(currentKey.keyName),
        parameterInfos = paramInfos,
      )
    val computeInvalidationReasonVariable = irTmpVariableInCurrentFun(
      computeInvalidationReason,
      nameHint = "$currentFunctionName\$validationReason",
    )
    newStatements += computeInvalidationReasonVariable

    // TODO: Improve default log message
    val defaultMessage = irString("[INVALIDATION_TRACKER] <${getCurrentFunctionNameIntercepttedAnonymous(currentUserProvideName)}> invalidation processed")
    val affectedComposable = InvalidationLogger.irAffectedComposable(
      composableName = irString(currentFunctionName),
      packageName = irString(getCurrentFunctionPackage()),
      filePath = irString(currentFunctionLocation.file),
      startLine = irInt(currentFunctionLocation.line),
      startColumn = irInt(currentFunctionLocation.column),
    )
    val invalidationTypeSymbol = InvalidationLogger.irInvalidationTypeSymbol()
    val invalidationTypeProcessed =
      InvalidationLogger.irInvalidationTypeProcessed(reason = irGetValue(computeInvalidationReasonVariable))
        .apply { type = invalidationTypeSymbol.defaultType }

    val logger = InvalidationLogger.irLog(
      affectedComposable = affectedComposable,
      invalidationType = invalidationTypeProcessed,
      defaultMessage = defaultMessage,
    )
    newStatements += logger

    expression.statements.addAll(1, newStatements)
    expression.origin = InvalidationTrackerOrigin

    val log = buildString {
      for ((index, statement) in expression.statements.withIndex()) {
        val dump = run {
          val dump = statement.dump().trimIndent()
          if (dump.length > 500) dump.substring(0, 500) + "..." else dump
        }
        if (index == 1) appendLine(">>>>>>>>>> ADD: ${logger.dump()}")
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

    return expression
  }

  override fun visitSkipToGroupEndCall(function: IrSimpleFunction, expression: IrCall): IrBlock {
    val currentKey = irTracee[DurableWritableSlices.DURABLE_FUNCTION_KEY, function]!!
    val currentUserProvideName = currentKey.userProvideName

    val currentFunctionLocation = function.getSafelyLocation()
    val currentFunctionName = currentUserProvideName ?: function.name.asString()

    // TODO: Improve default log message
    val defaultMessage = irString("[INVALIDATION_TRACKER] <${getCurrentFunctionNameIntercepttedAnonymous(currentUserProvideName)}> invalidation skipped")
    val affectedComposable = InvalidationLogger.irAffectedComposable(
      composableName = irString(currentFunctionName),
      packageName = irString(getCurrentFunctionPackage()),
      filePath = irString(currentFunctionLocation.file),
      startLine = irInt(currentFunctionLocation.line),
      startColumn = irInt(currentFunctionLocation.column),
    )
    val invalidationTypeSymbol = InvalidationLogger.irInvalidationTypeSymbol()
    val invalidationTypeSkipped = InvalidationLogger.irInvalidationTypeSkipped()
      .apply { type = invalidationTypeSymbol.defaultType }

    val logger = InvalidationLogger.irLog(
      affectedComposable = affectedComposable,
      invalidationType = invalidationTypeSkipped,
      defaultMessage = defaultMessage,
    )
    val block = IrBlockImpl(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      type = irBuiltIns.unitType,
      origin = InvalidationTrackerOrigin,
      statements = listOf(logger, expression),
    )

    logger("[invalidation skipped] transformed: ${expression.dumpKotlinLike()} -> ${block.dumpKotlinLike()}")

    return block
  }
}
