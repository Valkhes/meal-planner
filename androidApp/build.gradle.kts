plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.valkhes.mealplanner"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.valkhes.mealplanner"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    lint {
        abortOnError = true
        // English first, French native: a missing French string fails the build (README, constraint 8).
        error += "MissingTranslation"
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime") }
}

dependencies {
    implementation(project(":infrastructure"))
    implementation(project(":ui"))
    implementation(libs.activity.compose)
    implementation(libs.work.runtime)
    implementation(libs.datetime)
}
