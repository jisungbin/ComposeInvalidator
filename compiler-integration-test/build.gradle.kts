/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

@file:Suppress("UnstableApiUsage")

plugins {
  kotlin("jvm")
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  compilerOptions {
    optIn.addAll(
      "land.sungbin.composeinvestigator.runtime.ComposeInvestigatorCompilerApi",
      "land.sungbin.composeinvestigator.runtime.ExperimentalComposeInvestigatorApi",
    )
    freeCompilerArgs.addAll("-P", "plugin:land.sungbin.composeinvestigator.compiler:verbose=true")
  }
}

dependencies {
  implementation(projects.runtime)
  implementation(libs.compose.runtime)

  implementation("androidx.compose.runtime:runtime-test-utils:1.8.0-SNAPSHOT") {
    because("Why SNAPSHOT? See https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/runtime/runtime-test-utils/build.gradle;l=71;drc=214c6abe4e624304956276717a0163fad3858be9")
  }
  kotlinCompilerPluginClasspath(projects.compiler)

  testImplementation(kotlin("test-junit5", version = libs.versions.kotlin.core.get()))
  testImplementation(libs.test.kotlin.coroutines)
  testImplementation(libs.test.assertk)
}
