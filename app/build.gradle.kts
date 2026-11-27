plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// versionCode is the commit count, so every build of main is newer than the
// last. A shallow clone undercounts: run scripts/unshallow.sh before a release.
fun git(vararg args: String): String? = try {
    val result = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    result.standardOutput.asText.get().trim().takeIf { result.result.get().exitValue == 0 && it.isNotEmpty() }
} catch (e: Exception) {
    logger.warn("Version derivation: git ${args.joinToString(" ")} failed: ${e.message}")
    null
}

val commitCount: Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val shortSha: String = git("rev-parse", "--short", "HEAD") ?: "unknown"

android {
    namespace = "app.steadmo"
    // The SDK the session-start hook installs (.claude/hooks/session-start.sh).
    compileSdk = 37

    defaultConfig {
        applicationId = "app.steadmo"
        // Android 15: the CamAPS FX phones in use are current, and it keeps
        // notification-listener and POST_NOTIFICATIONS handling to one path.
        minSdk = 35
        targetSdk = 36
        versionCode = commitCount
        versionName = "$commitCount+$shortSha"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
