/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler.lower

import androidx.compose.compiler.plugins.kotlin.analysis.StabilityInferencer
import androidx.compose.compiler.plugins.kotlin.analysis.normalize
import androidx.compose.compiler.plugins.kotlin.irTrace
import land.sungbin.composeinvestigator.compiler.HandledMap
import land.sungbin.composeinvestigator.compiler.analysis.DurationWritableSlices
import land.sungbin.composeinvestigator.compiler.log
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.getCompilerMessageLocation
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.name.SpecialNames

internal class InvalidationProcessTracingFirstTransformer(
  context: IrPluginContext,
  private val stabilityInferencer: StabilityInferencer,
) : ComposeInvestigatorBaseLower(context) {
  private val handled = HandledMap()

  override fun firstTransformComposableBody(composable: IrSimpleFunction, body: IrBlockBody): IrBody {
    messageCollector.log(
      "Visit composable body: ${composable.name}",
      body.getCompilerMessageLocation(composable.file),
    )

    val table = tableByFile(composable.file)
    val scope = Scope(composable.symbol)

    val currentKey = context.irTrace[DurationWritableSlices.DURABLE_FUNCTION_KEY, composable] ?: return body
    if (!handled.handle(currentKey.keyName)) return body

    val newStatements = mutableListOf<IrStatement>()

    val currentValueArguments = scope.createTemporaryVariable(
      IrCallImpl.fromSymbolOwner(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        symbol = mutableListOfSymbol,
      ).apply {
        putTypeArgument(0, valueArgument.symbol.defaultType)
      },
      nameHint = "currentValueArguments",
    )
    newStatements += currentValueArguments

    for (param in composable.valueParameters) {
      // Synthetic arguments are not handled.
      if (param.name.asString().startsWith('$')) continue

      val name = irString(param.name.asString())
      val type = irString(param.type.classFqName?.asString() ?: SpecialNames.ANONYMOUS_STRING)
      val value = irGetValue(param)
      val valueString = irToString(value)
      val valueHashCode = irHashCode(value)
      val stability = stabilityInferencer.stabilityOf(value).normalize().asOwnStability()

      val valueArgumentVariable = scope.createTemporaryVariable(
        valueArgument(
          name = name,
          type = type,
          valueString = valueString,
          valueHashCode = valueHashCode,
          stability = stability,
        ),
        nameHint = "${param.name.asString()}\$valueArgu",
      )
      val addValueArgumentToList = IrCallImpl.fromSymbolOwner(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        symbol = mutableListAddSymbol,
      ).apply {
        dispatchReceiver = irGetValue(currentValueArguments)
        putValueArgument(0, irGetValue(valueArgumentVariable))
      }

      newStatements += valueArgumentVariable
      newStatements += addValueArgumentToList
    }

    val invalidationReasonVariable = scope.createTemporaryVariable(
      table.irComputeInvalidationReason(
        keyName = irString(currentKey.keyName),
        arguments = irGetValue(currentValueArguments),
      ),
      nameHint = "${composable.name.asString()}\$validationReason",
    )
    newStatements += invalidationReasonVariable

    val invalidationTypeProcessed = invalidationLogger.irInvalidationTypeProcessed(irGetValue(invalidationReasonVariable))
      .apply { type = invalidationLogger.irInvalidationTypeSymbol.defaultType }
    val logger = invalidationLogger.irLog(currentKey.composable, type = invalidationTypeProcessed)

    newStatements += logger

    body.statements.addAll(0, newStatements)

    return body.also {
      messageCollector.log(
        "Transform composable body succeed: ${composable.name}",
        body.getCompilerMessageLocation(composable.file),
      )
    }
  }
}
