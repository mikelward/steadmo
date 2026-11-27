plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

// Plain Kotlin on purpose (SPEC.md §6): the parser, trend and alert rules are
// tested on the JVM with no Android SDK.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
