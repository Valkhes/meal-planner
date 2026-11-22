plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets {
        commonMain.dependencies { api(libs.datetime) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
