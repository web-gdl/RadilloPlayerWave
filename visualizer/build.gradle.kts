plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.radillo.playerwave.visualizer"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // El visualizador solo depende de :core (modelos y tema). Nunca de :player ni de Media3.
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)

    // El paquete `analysis` es Kotlin puro: se prueba con JUnit sin Robolectric.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
