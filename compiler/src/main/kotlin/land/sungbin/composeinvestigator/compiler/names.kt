/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

// ===== PACKAGE ===== //

public const val AndroidxComposeRuntime: String = "androidx.compose.runtime"
public const val ComposeInvestigatorRuntime: String = "land.sungbin.composeinvestigator.runtime"

// ===== FULLY-QUALIFIED NAME ===== //

// START Kotlin/Java Standard Library
public val EMPTY_LIST_FQN: FqName = FqName("kotlin.collections.emptyList")

public val MUTABLE_LIST_OF_FQN: FqName = FqName("kotlin.collections.mutableListOf")
public val MUTABLE_LIST_ADD_FQN: FqName = FqName("kotlin.collections.MutableList.add")

public val HASH_CODE_FQN: FqName = FqName("kotlin.hashCode")
// END Kotlin/Java Standard Library

// START Compose Runtime
public val COMPOSER_FQN: FqName = FqName("$AndroidxComposeRuntime.Composer")
public val Composer_SKIP_TO_GROUP_END: Name = Name.identifier("skipToGroupEnd")

public val SCOPE_UPDATE_SCOPE_FQN: FqName = FqName("$AndroidxComposeRuntime.ScopeUpdateScope")
public val ScopeUpdateScope_UPDATE_SCOPE: Name = Name.identifier("updateScope")
// END Compose Runtime

// START NoInvestigation
public val NO_INVESTIGATION_FQN: FqName = FqName("$ComposeInvestigatorRuntime.NoInvestigation")
// END NoInvestigation

// START ComposeInvestigatorConfig
public val COMPOSE_INVESTIGATOR_CONFIG_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposeInvestigatorConfig")
public val ComposeInvestigatorConfig_LOGGER: Name = Name.identifier("logger")
// END ComposeInvestigatorConfig

// START ComposableInvalidationLogger
public val COMPOSABLE_INVALIDATION_LOGGER_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposableInvalidationLogger")
public val ComposableInvalidationLogger_LOG: Name = Name.identifier("log")

public val INVALIDATION_TYPE_FQN: FqName = FqName("$ComposeInvestigatorRuntime.InvalidationType")
public val InvalidationType_PROCESSED: Name = Name.identifier("Processed")
public val InvalidationType_SKIPPED: Name = Name.identifier("Skipped")

public val INVALIDATION_REASON_FQN: FqName = FqName("$ComposeInvestigatorRuntime.InvalidationReason")
public val InvalidationReason_Invalidate: Name = Name.identifier("Invalidate")
// END ComposableInvalidationLogger

// START ComposableInvalidationTraceTable
public val CURRENT_COMPOSABLE_INVALIDATION_TRACER_FQN: FqName = FqName("$ComposeInvestigatorRuntime.currentComposableInvalidationTracer")

public val COMPOSABLE_NAME_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposableName")

public val COMPOSABLE_INVALIDATION_TRACE_TABLE_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposableInvalidationTraceTable")
public val ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_NAME: Name = Name.identifier("currentComposableName")
public val ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_KEY_NAME: Name = Name.identifier("currentComposableKeyName")
public val ComposableInvalidationTraceTable_REGISTER_STATE_OBJECT: Name = Name.identifier("registerStateObject")
public val ComposableInvalidationTraceTable_COMPUTE_INVALIDATION_REASON: Name = Name.identifier("computeInvalidationReason")
// END ComposableInvalidationTraceTable

// START Stability
public val STABILITY_FQN: FqName = FqName("$ComposeInvestigatorRuntime.Stability")
public val Stability_CERTAIN: Name = Name.identifier("Certain")
public val Stability_RUNTIME: Name = Name.identifier("Runtime")
public val Stability_UNKNOWN: Name = Name.identifier("Unknown")
public val Stability_PARAMETER: Name = Name.identifier("Parameter")
public val Stability_COMBINED: Name = Name.identifier("Combined")
// END Stability

// START ChangedFields
public val COMPOSABLE_INFORMATION_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposableInformation")
public val CHANGED_ARGUMENT_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ChangedArgument")
// END ChangedFields

// START ValueFields
public val VALUE_PARAMETER_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ValueParameter")
public val VALUE_ARGUMENT_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ValueArgument")
// END affect/ValueFields

// TODO testing
public fun CallableId.Companion.fromFqName(fqName: FqName): CallableId {
  val paths = fqName.pathSegments()
  val lastUppercaseIndex = paths.indexOfLast { path -> path.asString().first().isUpperCase() }

  if (lastUppercaseIndex == paths.lastIndex) {
    // a.b.c.D -> packageName, callableName
    return CallableId(
      packageName = FqName(paths.subList(0, /* exclusive */ lastUppercaseIndex).joinToString(".", transform = Name::asString)),
      callableName = paths.last(),
    )
  }

  if (lastUppercaseIndex != -1) {
    val firstUppercaseIndex = paths.indexOfFirst { path -> path.asString().first().isUpperCase() }

    return if (firstUppercaseIndex == lastUppercaseIndex) {
      // a.b.c.D.e -> packageName, className, callableName
      CallableId(
        packageName = FqName(paths.subList(0, /* exclusive */ lastUppercaseIndex).joinToString(".", transform = Name::asString)),
        className = FqName(paths[lastUppercaseIndex].asString()),
        callableName = paths.last(),
      )
    } else {
      // a.b.c.D.E.f -> packageName, classNames callableName
      CallableId(
        packageName = FqName(paths.subList(0, /* exclusive */ firstUppercaseIndex).joinToString(".", transform = Name::asString)),
        className = FqName(paths.subList(firstUppercaseIndex, /* exclusive */ lastUppercaseIndex + 1).joinToString(".", transform = Name::asString)),
        callableName = paths.last(),
      )
    }
  }

  // a.b.c.d -> packageName, callableName
  check(lastUppercaseIndex == -1) { "The CallableId parsing logic for the given FqName is invalid. (fqName=${fqName.asString()})" }
  return CallableId(
    packageName = FqName(paths.subList(0, /* exclusive */ paths.lastIndex).joinToString(".", transform = Name::asString)),
    callableName = paths.last(),
  )
}

@Suppress("UnusedReceiverParameter")
public val SpecialNames.UNKNOWN_STRING: String get() = "<unknown>"
