// Copyright 2024 Ji Sungbin
// SPDX-License-Identifier: Apache-2.0
package land.sungbin.composeinvestigator.compiler._compilation

import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import androidx.compose.compiler.plugins.kotlin.lower.dumpSrc
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.io.File
import java.util.EnumSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import land.sungbin.composeinvestigator.compiler.ComposeInvestigatorFirExtensionRegistrar
import land.sungbin.composeinvestigator.compiler.ComposeInvestigatorFirstPhaseExtension
import land.sungbin.composeinvestigator.compiler.ComposeInvestigatorLastPhaseExtension
import land.sungbin.composeinvestigator.compiler.FeatureFlag
import land.sungbin.composeinvestigator.compiler._compilation.GoldenUtil.dumpSrcForGolden
import land.sungbin.composeinvestigator.compiler._source.sourcePath
import land.sungbin.composeinvestigator.compiler._source.sourceString
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.com.intellij.util.PathUtil
import org.jetbrains.kotlin.compiler.plugin.registerExtensionsForTest
import org.jetbrains.kotlin.config.AnalysisFlag
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.IrVerificationMode
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.pipeline.Fir2IrActualizedResult
import org.jetbrains.kotlin.ir.declarations.IrFile

abstract class AbstractCompilerTest(
  private val features: EnumSet<FeatureFlag> = NO_FEATURES,
  private val sourceRoot: String? = null,
) {
  private val disposable = Disposer.newDisposable()

  @BeforeTest fun setSystemProperties() {
    System.setProperty("idea.home", homeDir)
    System.setProperty("user.dir", homeDir)
    System.setProperty("idea.ignore.disabled.plugins", "true")
  }

  @AfterTest fun disposeTestRootDisposable() {
    Disposer.dispose(disposable)
  }

  @Suppress("UnstableApiUsage")
  private fun createK2Compiler(
    features: EnumSet<FeatureFlag> = this.features,
    quiet: Boolean = false,
  ) = KotlinK2Compiler.create(
    disposable = disposable,
    quiet = quiet,
    updateConfiguration = {
      val languageVersion = LanguageVersion.fromFullVersionString(KotlinVersion.CURRENT.toString())!!.also { version ->
        check(version.usesK2) { "Kotlin version $version is not a K2 version" }
      }
      val analysisFlags = mapOf<AnalysisFlag<*>, Any>(
        AnalysisFlags.allowUnstableDependencies to true,
        AnalysisFlags.skipPrereleaseCheck to true,
        AnalysisFlags.optIn to listOf(
          "land.sungbin.composeinvestigator.runtime.ComposeInvestigatorCompilerApi",
          "land.sungbin.composeinvestigator.runtime.ExperimentalComposeInvestigatorApi",
        ),
      )

      languageVersionSettings = LanguageVersionSettingsImpl(
        languageVersion = languageVersion,
        apiVersion = ApiVersion.createByLanguageVersion(languageVersion),
        analysisFlags = analysisFlags,
      )

      configureJdkClasspathRoots()
      addJvmClasspathRoots(defaultClasspaths)

      if (!getBoolean(JVMConfigurationKeys.NO_JDK) && get(JVMConfigurationKeys.JDK_HOME) == null) {
        put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home")!!))
      }
    },
    registerExtensions = { configuration ->
      registerExtensionsForTest(this, configuration) {
        FirExtensionRegistrarAdapter.registerExtension(ComposeInvestigatorFirExtensionRegistrar())
        with(ComposePluginRegistrar) { registerCommonExtensions() }
      }
      extensionArea.getExtensionPoint(IrGenerationExtension.extensionPointName).run {
        registerExtension(
          ComposeInvestigatorFirstPhaseExtension(configuration.messageCollector, IrVerificationMode.ERROR, features),
          LoadingOrder.FIRST,
          this@create,
        )
        registerExtension(
          ComposeInvestigatorLastPhaseExtension(configuration.messageCollector, IrVerificationMode.ERROR, features),
          LoadingOrder.LAST,
          this@create,
        )
      }
      IrGenerationExtension.registerExtension(
        this,
        ComposePluginRegistrar.createComposeIrExtension(configuration),
      )
    },
  )

  protected fun analyze(file: SourceFile): FirAnalysisResult =
    createK2Compiler().analyze(file)

  protected fun compile(file: SourceFile): Fir2IrActualizedResult =
    createK2Compiler().compile(file)

  protected fun clean(file: SourceFile) = diff(file) { "" }

  protected fun diff(file: SourceFile, contextSize: Int = 5, expect: () -> String) {
    val original = createK2Compiler(NO_FEATURES, quiet = true).compile(file).irModuleFragment.files.single()
    val transformed = createK2Compiler().compile(file).irModuleFragment.files.single()

    val originalLines = original.dumpSrcForGolden(source = file.source).lines()
    val transformedLines = transformed.dumpSrcForGolden(source = file.source).lines()

    val diff = UnifiedDiffUtils.generateUnifiedDiff(
      /* originalFileName = */ "original-code.kt",
      /* revisedFileName = */ "transformed-code.kt",
      /* originalLines = */ originalLines,
      /* patch = */ DiffUtils.diff(originalLines, transformedLines),
      /* contextSize = */ contextSize,
    )
    assertEquals(expect().trim(), diff.drop(2).joinToString(separator = "\n"))
  }

  protected fun source(filename: String): SourceFile {
    val resolvedFilename = sourceRoot?.let { "$it/$filename" } ?: filename
    return SourceFile(
      name = resolvedFilename.substringAfterLast('/'),
      source = sourceString(resolvedFilename),
      path = sourcePath(resolvedFilename).substringBeforeLast('/'),
    )
  }

  companion object {
    private fun File.applyExistenceCheck(): File = apply {
      if (!exists()) throw NoSuchFileException(this)
    }

    private val homeDir: String = run {
      val userDir = System.getProperty("user.dir")
      val dir = File(userDir ?: ".")
      val path = FileUtil.toCanonicalPath(dir.absolutePath)
      File(path).applyExistenceCheck().absolutePath
    }

    private val NO_FEATURES = EnumSet.noneOf(FeatureFlag::class.java)

    // https://github.com/JetBrains/kotlin/blob/bb25d2f8aa74406ff0af254b2388fd601525386a/plugins/compose/compiler-hosted/integration-tests/src/jvmTest/kotlin/androidx/compose/compiler/plugins/kotlin/AbstractCompilerTest.kt#L212-L228
    private val defaultClasspaths by lazy {
      fun jar(clazz: Class<*>) = File(PathUtil.getJarPathForClass(clazz))

      listOf(
        jar(Unit::class.java),
        jar(land.sungbin.composeinvestigator.runtime.ComposeInvestigator::class.java),
        jar(kotlinx.coroutines.CoroutineScope::class.java),
        jar(androidx.compose.runtime.Composable::class.java),
      )
    }
  }
}

private object GoldenUtil {
  private val MatchResult.text get() = groupValues[0]
  private fun MatchResult.number() = groupValues[1].toInt()
  private fun MatchResult.isChar(c: String) = text == c
  private fun MatchResult.isNumber() = groupValues[1].isNotEmpty()
  private fun MatchResult.isFileName() = groups[4] != null

  @Suppress("RegExpSimplifiable")
  fun IrFile.dumpSrcForGolden(source: String): String {
    val keySet = mutableListOf<Int>()
    return this
      .dumpSrc(useFir = true)
      .replace('$', '%')
      // replace source keys for start group calls
      .replace(Regex("(%composer\\.start(Restart|Movable|Replaceable|Replace)Group\\()-?((0b)?[-\\d]+)")) { match ->
        val stringKey = match.groupValues[3]
        val key = if (stringKey.startsWith("0b")) Integer.parseInt(stringKey.drop(2), 2) else stringKey.toInt()
        if (key in keySet) {
          "${match.groupValues[1]}<!DUPLICATE KEY: $key!>"
        } else {
          keySet.add(key)
          "${match.groupValues[1]}<>"
        }
      }
      .replace(Regex("(sourceInformationMarkerStart\\(%composer, )([-\\d]+)")) { match ->
        "${match.groupValues[1]}<>"
      }
      // replace traceEventStart values with a token
      .replace(Regex("traceEventStart\\(-?\\d+, (%dirty|%changed|-1), (%dirty1|%changed1|-1), (.*)")) { match ->
        "traceEventStart(<>, ${match.groupValues[1]}, ${match.groupValues[2]}, <>)"
      }
      // replace source information with source it references
      .replace(Regex("(%composer\\.start(Restart|Movable|Replaceable|Replace)Group\\([^\"\\n]*)\"(.*)\"\\)")) { match ->
        "${match.groupValues[1]}\"${generateSourceInfo(match.groupValues[4], source)}\")"
      }
      .replace(Regex("(sourceInformation(MarkerStart)?\\(.*)\"(.*)\"\\)")) { match ->
        "${match.groupValues[1]}\"${generateSourceInfo(match.groupValues[3], source)}\")"
      }
      .replace(Regex("(composableLambda[N]?\\([^\"\\n]*)\"(.*)\"\\)")) { match ->
        "${match.groupValues[1]}\"${generateSourceInfo(match.groupValues[2], source)}\")"
      }
      .replace(Regex("(rememberComposableLambda[N]?)\\((-?\\d+)")) { match ->
        "${match.groupValues[1]}(<>"
      }
      // replace source keys for joinKey calls
      .replace(Regex("(%composer\\.joinKey\\()([-\\d]+)")) { match ->
        "${match.groupValues[1]}<>"
      }
      // composableLambdaInstance(<>, true)
      .replace(Regex("(composableLambdaInstance\\()([-\\d]+, (true|false))")) { match ->
        "${match.groupValues[1]}<>, ${match.groupValues[3]}"
      }
      // composableLambda(%composer, <>, true)
      .replace(Regex("(composableLambda\\(%composer,\\s)([-\\d]+)")) { match ->
        "${match.groupValues[1]}<>"
      }
      .trimIndent()
      .trimTrailingWhitespaces()
  }

  @Suppress("RegExpSimplifiable")
  private fun generateSourceInfo(sourceInfo: String, source: String): String {
    val regex = Regex("(\\d+)|([,])|([*])|([:])|C(\\(.*\\))?|L|(P\\(*\\))|@")

    var current = 0
    var currentResult = regex.find(sourceInfo, current)
    var result = ""

    fun next(): MatchResult? {
      currentResult?.let { match ->
        current = match.range.last + 1
        currentResult = match.next()
      }
      return currentResult
    }

    // A location has the format: [<line-number>]['@' <offset> ['L' <length>]]
    // where the named productions are numbers
    fun parseLocation(): String? {
      var mr = currentResult
      if (mr != null && mr.isNumber()) {
        // line number, we ignore the value in during testing.
        mr = next()
      }
      if (mr != null && mr.isChar("@")) {
        // Offset
        mr = next()
        if (mr == null || !mr.isNumber()) {
          return null
        }
        val offset = mr.number()
        mr = next()
        var ellipsis = ""
        val maxFragment = 6
        val rawLength = if (mr != null && mr.isChar("L")) {
          mr = next()
          if (mr == null || !mr.isNumber()) {
            return null
          }
          mr.number().also { next() }
        } else {
          maxFragment
        }
        val eol = source.indexOf('\n', offset).let {
          if (it < 0) source.length else it
        }
        val space = source.indexOf(' ', offset).let {
          if (it < 0) source.length else it
        }
        val maxEnd = offset + maxFragment
        if (eol > maxEnd && space > maxEnd) ellipsis = "..."
        val length = minOf(maxEnd, minOf(offset + rawLength, space, eol)) - offset
        return "<${source.substring(offset, offset + length)}$ellipsis>"
      }
      return null
    }

    while (currentResult != null) {
      val mr = currentResult!!
      if (mr.range.first != current) {
        return "invalid source info at $current: '$sourceInfo'"
      }
      when {
        mr.isNumber() || mr.isChar("@") -> {
          val fragment = parseLocation() ?: return "invalid source info at $current: '$sourceInfo'"
          result += fragment
        }
        mr.isFileName() -> {
          return result + ":" + sourceInfo.substring(mr.range.last + 1)
        }
        else -> {
          result += mr.text
          next()
        }
      }
      require(mr != currentResult) { "regex didn't advance" }
    }

    if (current != sourceInfo.length) return "invalid source info at $current: '$sourceInfo'"

    return result
  }

  private fun String.trimTrailingWhitespaces(): String =
    split('\n').joinToString("\n", transform = String::trimEnd)
}
