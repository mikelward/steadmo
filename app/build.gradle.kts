plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

// Screenshot tests write PNGs only when asked: -Proborazzi.test.record=true.
tasks.withType<Test>().configureEach {
    // Robolectric's native graphics read FileDescriptor internals on JDK 17+.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    if (project.hasProperty("roborazzi.test.record")) {
        jvmArgs("-Droborazzi.test.record=true")
        outputs.upToDateWhen { false }
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
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
