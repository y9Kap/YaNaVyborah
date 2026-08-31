plugins { alias(libs.plugins.android.library) }

android {
    namespace = "org.yanavybori.core.navigation"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
