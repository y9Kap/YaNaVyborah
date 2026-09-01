plugins { alias(libs.plugins.android.library) }

android {
    namespace = "org.yanavybori.core.search"
    compileSdk { version = release(36) }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
