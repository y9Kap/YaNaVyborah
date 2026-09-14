import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "webApp"
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    open = false
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain {
            resources.srcDir("../shared/src/commonMain/resources")
            resources.srcDir("../docs/branding")
            resources.srcDir("../data")
            dependencies {
                implementation(project(":shared"))
                implementation(project(":core:common"))
                implementation(project(":core:content"))
                implementation(project(":core:crypto"))
                implementation(project(":core:model"))
                implementation(project(":core:ui"))
                implementation(project(":feature:observer"))
                implementation(libs.cmp.runtime)
                implementation(libs.cmp.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        wasmJsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
