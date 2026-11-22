plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime") }
    jvm()
    androidTarget()
    sourceSets {
        commonMain.dependencies {
            api(project(":application"))
            implementation(libs.sqlite)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}

android {
    namespace = "dev.valkhes.mealplanner.infrastructure"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
}
