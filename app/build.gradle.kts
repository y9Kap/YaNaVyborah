plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val configuredVersionName = providers.gradleProperty("VERSION_NAME").orNull ?: "0.1.0"
val configuredVersionCode = providers.gradleProperty("VERSION_CODE").orNull?.let { value ->
    value.toIntOrNull()?.takeIf { it > 0 }
        ?: error("VERSION_CODE должен быть положительным целым числом")
} ?: 1

val releaseKeystorePath = providers.environmentVariable("YANAVYBORAH_KEYSTORE_FILE").orNull
val releaseKeystorePassword = providers.environmentVariable("YANAVYBORAH_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("YANAVYBORAH_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("YANAVYBORAH_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
check(releaseSigningValues.none { !it.isNullOrBlank() } || hasReleaseSigning) {
    "Для release-подписи необходимо задать все переменные YANAVYBORAH_KEYSTORE_* и YANAVYBORAH_KEY_*"
}

android {
    namespace = "org.yanavybori.app"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "org.yanavybori.app"
        minSdk = 24
        targetSdk = 37
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            optimization { enable = false }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:content"))
    implementation(project(":core:crypto"))
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:search"))
    implementation(project(":core:ui"))
    implementation(project(":feature:observer"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:voter"))
    implementation(project(":feature:workpressure"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
