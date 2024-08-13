/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler.lower

import androidx.compose.compiler.plugins.kotlin.EmptyModuleMetrics
import androidx.compose.compiler.plugins.kotlin.FeatureFlags
import androidx.compose.compiler.plugins.kotlin.analysis.StabilityInferencer
import androidx.compose.compiler.plugins.kotlin.irTrace
import androidx.compose.compiler.plugins.kotlin.lower.ComposableSymbolRemapper
import androidx.compose.compiler.plugins.kotlin.lower.DurableKeyTransformer
import androidx.compose.compiler.plugins.kotlin.lower.DurableKeyVisitor
import land.sungbin.composeinvestigator.compiler.analysis.ComposableKeyInfo
import land.sungbin.composeinvestigator.compiler.analysis.DurationWritableSlices
import land.sungbin.composeinvestigator.compiler.analysis.set
import land.sungbin.composeinvestigator.compiler.error
import land.sungbin.composeinvestigator.compiler.struct.IrComposableInformation
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.getCompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.name.FqName

public class DurableComposableKeyTransformer(
  context: IrPluginContext,
  private val irComposableInformation: IrComposableInformation,
  private val messageCollector: MessageCollector,
  stabilityInferencer: StabilityInferencer,
  featureFlags: FeatureFlags = FeatureFlags(), // TODO supports this feature
) : DurableKeyTransformer(
  context = context,
  keyVisitor = DurableKeyVisitor(),
  symbolRemapper = ComposableSymbolRemapper(),
  stabilityInferencer = stabilityInferencer,
  metrics = EmptyModuleMetrics,
  featureFlags = featureFlags,
) {
  private var currentKeys = mutableListOf<ComposableKeyInfo>()

  override fun visitFile(declaration: IrFile): IrFile {
    val stringKeys = mutableSetOf<String>()
    return root(stringKeys) {
      val prev = currentKeys
      val next = mutableListOf<ComposableKeyInfo>()
      try {
        currentKeys = next
        super.visitFile(declaration)
      } finally {
        currentKeys = prev
      }
    }
  }

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
    val (keyName, success) = buildKey("fun-${declaration.signatureString()}")
    if (!success) messageCollector.error("Duplicate key: $keyName", declaration.getCompilerMessageLocation(declaration.file))

    val composable = irComposableInformation(
      name = context.irString(declaration.name.asString()),
      packageName = context.irString((declaration.fqNameWhenAvailable?.parent() ?: FqName.ROOT).asString()),
      fileName = context.irString(declaration.file.name),
    )

    val keyInfo = ComposableKeyInfo(keyName = keyName, composable = composable)
    currentKeys += keyInfo
    context.irTrace[DurationWritableSlices.DURABLE_FUNCTION_KEY, declaration] = keyInfo

    return super.visitSimpleFunction(declaration)
  }
}
