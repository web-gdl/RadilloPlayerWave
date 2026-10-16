plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.radillo.playerwave.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Sin Compose: modelos, perfil del dispositivo, utilidades y carga de carátulas con android.graphics.
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.palette)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
