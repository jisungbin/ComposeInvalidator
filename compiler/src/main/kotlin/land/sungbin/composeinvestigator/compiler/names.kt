/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

package land.sungbin.composeinvestigator.compiler

import land.sungbin.fastlist.fastJoinToString
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

// ===== PACKAGE ===== //

public const val AndroidxComposeRuntime: String = "androidx.compose.runtime"
public const val ComposeInvestigatorRuntime: String = "land.sungbin.composeinvestigator.runtime"
public const val ComposeInvestigatorRuntimeAffect: String = "land.sungbin.composeinvestigator.runtime.affect"

// ===== FULLY-QUALIFIED NAME ===== //

// START Kotlin/Java Standard Library
public val ITERABLE_TO_LIST_FQN: FqName = FqName("kotlin.collections.toList")
public val EMPTY_LIST_FQN: FqName = FqName("kotlin.collections.emptyList")

public val MUTABLE_LIST_OF_FQN: FqName = FqName("kotlin.collections.mutableListOf")
public val MUTABLE_LIST_ADD_FQN: FqName = FqName("kotlin.collections.MutableList.add")

public val HASH_CODE_FQN: FqName = FqName("kotlin.hashCode")

public val STACK_FQN: FqName = FqName("java.util.Stack")
public val Stack_PUSH: Name = Name.identifier("push")
public val Stack_POP: Name = Name.identifier("pop")
// END Kotlin/Java Standard Library

// START Compose Runtime
public val COMPOSER_FQN: FqName = FqName("$AndroidxComposeRuntime.Composer")

public val Composer_START_RESTART_GROUP: Name = Name.identifier("startRestartGroup")
public val Composer_SKIPPING: Name = Name.identifier("skipping")
public val Composer_SKIP_TO_GROUP_END: Name = Name.identifier("skipToGroupEnd")

public val SCOPE_UPDATE_SCOPE_FQN: FqName = FqName("$AndroidxComposeRuntime.ScopeUpdateScope")
public val ScopeUpdateScope_UPDATE_SCOPE: Name = Name.identifier("updateScope")

public val STATE_FQN: FqName = FqName("$AndroidxComposeRuntime.State")
// END Compose Runtime

// START Compose Animation
public val ANIMATABLE_FQN: FqName = FqName("androidx.compose.animation.core.Animatable")
// END Compose Animation

// START NoInvestigation
public val NO_INVESTIGATION_FQN: FqName = FqName("$ComposeInvestigatorRuntime.NoInvestigation")
// END NoInvestigation

// START ComposeInvestigatorConfig
public val COMPOSE_INVESTIGATOR_CONFIG_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposeInvestigatorConfig")
public val ComposeInvestigatorConfig_INVALIDATION_LOGGER: Name = Name.identifier("invalidationLogger")
// END ComposeInvestigatorConfig

// START ComposableInvalidationLogger
public val COMPOSABLE_INVALIDATION_LOGGER_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposableInvalidationLogger")
public val ComposableInvalidationLogger_INVOKE: Name = Name.identifier("invoke")

public val INVALIDATION_REASON_FQN: FqName = FqName("$ComposeInvestigatorRuntime.InvalidationReason")
public val InvalidationReason_Invalidate: Name = Name.identifier("Invalidate")

public val COMPOSABLE_INVALIDATION_TYPE_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposableInvalidationType")
public val ComposableInvalidationType_PROCESSED: Name = Name.identifier("Processed")
public val ComposableInvalidationType_SKIPPED: Name = Name.identifier("Skipped")
// END ComposableInvalidationLogger

// START ComposableInvalidationTraceTable
public val CURRENT_COMPOSABLE_INVALIDATION_TRACER_FQN: FqName = FqName("$ComposeInvestigatorRuntime.currentComposableInvalidationTracer")

public val COMPOSABLE_NAME_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposableName")

public val COMPOSABLE_INVALIDATION_TRACE_TABLE_FQN: FqName = FqName("$ComposeInvestigatorRuntime.ComposableInvalidationTraceTable")
public val ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_NAME: Name = Name.identifier("currentComposableName")
public val ComposableInvalidationTraceTable_CURRENT_COMPOSABLE_KEY_NAME: Name = Name.identifier("currentComposableKeyName")
public val ComposableInvalidationTraceTable_CALL_LISTENERS: Name = Name.identifier("callListeners")
public val ComposableInvalidationTraceTable_COMPUTE_INVALIDATION_REASON: Name = Name.identifier("computeInvalidationReason")
// END ComposableInvalidationTraceTable

// START StateObjectTracer
public val REGISTER_STATE_OBJECT_TRACING_FQN: FqName = FqName("$ComposeInvestigatorRuntime.registerStateObjectTracing")
// END StateObjectTracer

// START DeclarationStability
public val DECLARATION_STABILITY_FQN: FqName = FqName("$ComposeInvestigatorRuntime.DeclarationStability")
public val DeclarationStability_CERTAIN: Name = Name.identifier("Certain")
public val DeclarationStability_RUNTIME: Name = Name.identifier("Runtime")
public val DeclarationStability_UNKNOWN: Name = Name.identifier("Unknown")
public val DeclarationStability_PARAMETER: Name = Name.identifier("Parameter")
public val DeclarationStability_COMBINED: Name = Name.identifier("Combined")
// END DeclarationStability

// START affect/AffectedField
public val AFFECTED_FIELD_FQN: FqName = FqName("$ComposeInvestigatorRuntimeAffect.AffectedField")
public val AffectedField_VALUE_PARAMETER: Name = Name.identifier("ValueParameter")
// END affect/AffectableField

// START affect/AffectedComposable
public val AFFECTED_COMPOSABLE_FQN: FqName = FqName("$ComposeInvestigatorRuntimeAffect.AffectedComposable")
// END affect/AffectedComposable

// TODO testing
public fun CallableId.Companion.fromFqName(fqName: FqName): CallableId {
  val paths = fqName.pathSegments() as List<Name>
  val lastUppercaseIndex = paths.indexOfLast { path -> path.asString().first().isUpperCase() }

  if (lastUppercaseIndex == paths.lastIndex) {
    // a.b.c.D -> packageName, callableName
    return CallableId(
      packageName = FqName(paths.subList(0, /* exclusive */ lastUppercaseIndex).fastJoinToString(".", transform = Name::asString)),
      callableName = paths.last(),
    )
  }

  if (lastUppercaseIndex != -1) {
    val firstUppercaseIndex = paths.indexOfFirst { path -> path.asString().first().isUpperCase() }

    return if (firstUppercaseIndex == lastUppercaseIndex) {
      // a.b.c.D.e -> packageName, className, callableName
      CallableId(
        packageName = FqName(paths.subList(0, /* exclusive */ lastUppercaseIndex).fastJoinToString(".", transform = Name::asString)),
        className = FqName(paths[lastUppercaseIndex].asString()),
        callableName = paths.last(),
      )
    } else {
      // a.b.c.D.E.f -> packageName, classNames callableName
      CallableId(
        packageName = FqName(paths.subList(0, /* exclusive */ firstUppercaseIndex).fastJoinToString(".", transform = Name::asString)),
        className = FqName(paths.subList(firstUppercaseIndex, /* exclusive */ lastUppercaseIndex + 1).fastJoinToString(".", transform = Name::asString)),
        callableName = paths.last(),
      )
    }
  }

  // a.b.c.d -> packageName, callableName
  check(lastUppercaseIndex == -1) {
    "The CallableId parsing logic for the given FqName is invalid. (fqName=${fqName.asString()})"
  }
  return CallableId(
    packageName = FqName(paths.subList(0, /* exclusive */ paths.lastIndex).fastJoinToString(".", transform = Name::asString)),
    callableName = paths.last(),
  )
}

@Suppress("UnusedReceiverParameter")
public val SpecialNames.UNKNOWN_STRING: String get() = "<unknown>"
