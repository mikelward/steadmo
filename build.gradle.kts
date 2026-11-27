plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
}

// One coverage report across :core and :app's unit tests: ./gradlew koverHtmlReport.
dependencies {
    kover(project(":core"))
    kover(project(":app"))
}

kover {
    reports {
        // Catch blocks for platform failures are most of what is left uncovered.
        verify {
            rule("Line coverage") {
                minBound(95)
            }
        }
        filters {
            excludes {
                // Generated code, not ours to cover.
                classes("app.steadmo.R", "app.steadmo.R$*", "app.steadmo.BuildConfig", "*ComposableSingletons*")
            }
        }
    }
}
