/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.theme.ThemeType
import com.diffplug.gradle.spotless.BaseKotlinExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version libs.versions.kotlin.core
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.gradle.test.logging) apply false
  alias(libs.plugins.gradle.publish.maven) apply false
  idea
}

idea {
  module {
    excludeDirs = buildSet {
      add(file("runtime/api"))
      add(file("documentation"))
      addAll(excludeDirs)
      addAll(allprojects.map { project -> project.file(".kotlin") })
    }
  }
}

allprojects {
  apply {
    plugin(rootProject.libs.plugins.spotless.get().pluginId)
  }

  extensions.configure<SpotlessExtension> {
    fun BaseKotlinExtension.useKtlint() {
      ktlint(rootProject.libs.versions.ktlint.get()).editorConfigOverride(
        mapOf(
          "indent_size" to "2",
          "ktlint_standard_filename" to "disabled",
          "ktlint_standard_package-name" to "disabled",
          "ktlint_standard_function-naming" to "disabled",
          "ktlint_standard_property-naming" to "disabled",
          "ktlint_standard_backing-property-naming" to "disabled",
          "ktlint_standard_class-signature" to "disabled",
          "ktlint_standard_import-ordering" to "disabled",
          "ktlint_standard_blank-line-before-declaration" to "disabled",
          "ktlint_standard_spacing-between-declarations-with-annotations" to "disabled",
          "ktlint_standard_max-line-length" to "disabled",
          "ktlint_standard_annotation" to "disabled",
          "ktlint_standard_multiline-if-else" to "disabled",
          "ktlint_standard_value-argument-comment" to "disabled",
          "ktlint_standard_value-parameter-comment" to "disabled",
          "ktlint_standard_comment-wrapping" to "disabled",
        ),
      )
    }

    kotlin {
      target("**/*.kt")
      targetExclude("**/build/**/*.kt", "spotless/*.kt")
      useKtlint()
      licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
    }
    kotlinGradle {
      target("**/*.kts")
      targetExclude("**/build/**/*.kts", "spotless/*.kts")
      useKtlint()
      // Look for the first line that doesn't have a block comment (assumed to be the license)
      licenseHeaderFile(rootProject.file("spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
    }
    format("xml") {
      target("**/*.xml")
      targetExclude("**/build/**/*.xml", "spotless/*.xml")
      // Look for the first XML tag that isn't a comment (<!--) or the xml declaration (<?xml)
      licenseHeaderFile(rootProject.file("spotless/copyright.xml"), "(<[^!?])")
    }
  }

  tasks.withType<KotlinCompile> {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_17
      optIn.addAll(
        "kotlin.OptIn",
        "kotlin.RequiresOptIn",
        "kotlin.contracts.ExperimentalContracts",
      )
    }
  }
}

subprojects {
  group = project.property("GROUP") as String
  version = project.property("VERSION_NAME") as String

  apply {
    plugin(rootProject.libs.plugins.gradle.test.logging.get().pluginId)
  }

  extensions.configure<TestLoggerExtension> {
    theme = ThemeType.MOCHA_PARALLEL
    slowThreshold = 10_000
  }

  tasks.withType<Test> {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
  }
}

tasks.register<Delete>("cleanAll") {
  delete(*allprojects.map { project -> project.layout.buildDirectory }.toTypedArray())
}
