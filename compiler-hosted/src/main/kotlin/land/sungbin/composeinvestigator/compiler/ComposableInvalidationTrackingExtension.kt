/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler

import androidx.compose.compiler.plugins.kotlin.analysis.StabilityInferencer
import land.sungbin.composeinvestigator.compiler.lower.ComposableInvalidationTrackingTransformer
import land.sungbin.composeinvestigator.compiler.lower.DurableComposableKeyTransformer
import land.sungbin.composeinvestigator.compiler.struct.IrAffectedComposable
import land.sungbin.composeinvestigator.compiler.struct.IrAffectedField
import land.sungbin.composeinvestigator.compiler.struct.IrInvalidationLogger
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

public class ComposableInvalidationTrackingExtension(private val logger: VerboseLogger) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val invalidationLogger = IrInvalidationLogger(pluginContext)
    val affectedField = IrAffectedField(pluginContext)
    val affectedComposable = IrAffectedComposable(pluginContext)

    val stabilityInferencer = StabilityInferencer(
      currentModule = moduleFragment.descriptor,
      externalStableTypeMatchers = emptySet(), // TODO: support this field
    )

    moduleFragment.transformChildrenVoid(
      DurableComposableKeyTransformer(
        context = pluginContext,
        stabilityInferencer = stabilityInferencer,
        affectedComposable = affectedComposable,
      ),
    )
    moduleFragment.transformChildrenVoid(
      ComposableInvalidationTrackingTransformer(
        context = pluginContext,
        logger = logger,
        stabilityInferencer = stabilityInferencer,
        affectedField = affectedField,
        affectedComposable = affectedComposable,
        invalidationLogger = invalidationLogger,
      ),
    )
  }
}
