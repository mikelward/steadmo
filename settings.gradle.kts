pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // mikelward/androidlog, the shared debug log and bug report, publishes
        // a Maven repository into its `maven` branch. Scoped to its one group.
        maven {
            name = "androidlog"
            url = uri("https://raw.githubusercontent.com/mikelward/androidlog/maven")
            content { includeGroup("com.mikelward.androidlog") }
        }
    }
}

rootProject.name = "Steadmo"

// `:core` is a plain Kotlin JVM module (SPEC.md §6): parsing, trend and alert
// logic cannot reach for a Context, so `./gradlew :core:test` needs no SDK.
include(":app")
include(":core")
