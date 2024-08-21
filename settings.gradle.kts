/*
 * Developed by Ji Sungbin 2024.
 *
 * Licensed under the MIT.
 * Please see full license: https://github.com/jisungbin/ComposeInvestigator/blob/main/LICENSE
 */

@file:Suppress("UnstableApiUsage")

rootProject.name = "ComposeInvestigator"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
// enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

pluginManagement {
  repositories {
    gradlePluginPortal()
    google {
      mavenContent {
        includeGroupByRegex(".*google.*")
        includeGroupByRegex(".*android.*")
      }
    }
    mavenCentral {
      mavenContent {
        releasesOnly()
      }
    }
    maven("https://jitpack.io") {
      mavenContent {
        includeGroup("com.github.takahirom")
      }
    }
    mavenLocal()
  }

  resolutionStrategy.eachPlugin {
    if (requested.id.id == "com.github.takahirom.decomposer") {
      useModule("com.github.takahirom:decomposer:main-SNAPSHOT")
    }
  }
}

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    google {
      mavenContent {
        includeGroupByRegex(".*google.*")
        includeGroupByRegex(".*android.*")
        releasesOnly()
      }
    }
    mavenCentral {
      mavenContent {
        releasesOnly()
      }
    }
    maven("https://androidx.dev/snapshots/builds/12257077/artifacts/repository") {
      mavenContent {
        snapshotsOnly()
      }
    }
    mavenLocal()
  }
}

include(
  ":runtime",
  ":compiler",
  ":compiler-integration-test",
  ":compiler-gradle-plugin",
  // ":sample",
)
