plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
    androidTarget()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":application"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
        }
    }
}

android {
    namespace = "dev.valkhes.mealplanner.ui"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "dev.valkhes.mealplanner.ui.generated"
}
