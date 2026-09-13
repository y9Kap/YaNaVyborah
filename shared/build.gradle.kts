plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "org.yanavybori.shared"
        compileSdk = 36
        minSdk = 24
        withHostTestBuilder {}
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:content"))
            implementation(project(":core:navigation"))
            api(project(":feature:observer"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:voter"))
            implementation(project(":feature:workpressure"))
            api(project(":core:ui"))
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.material3)
            implementation(libs.cmp.material.icons)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}
