/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

@file:Suppress("DEPRECATION", "UnstableApiUsage")

package land.sungbin.composeinvestigator.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(ComponentRegistrar::class)
public class ComposeInvestigatorPluginRegistrar : ComponentRegistrar {
  override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
    val enabled = configuration[ComposeInvestigatorConfiguration.KEY_ENABLED] ?: true
    if (!enabled) return

    val verbose = configuration[ComposeInvestigatorConfiguration.KEY_VERBOSE] ?: false
    val logger = VerboseLogger(configuration).apply { if (verbose) verbose() }

    project.extensionArea
      .getExtensionPoint(IrGenerationExtension.extensionPointName)
      .registerExtension(
        ComposableCallstackTrackingExtension(logger = logger),
        LoadingOrder.FIRST,
        project,
      )

    project.extensionArea
      .getExtensionPoint(IrGenerationExtension.extensionPointName)
      .registerExtension(
        ComposableInvalidationTrackingExtension(logger = logger),
        LoadingOrder.LAST,
        project,
      )
  }
}
