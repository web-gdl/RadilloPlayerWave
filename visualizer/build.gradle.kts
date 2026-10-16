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

    testOptions {
        // Las escenas crean android.graphics.Path en su construcción; en la JVM los stubs del
        // SDK devuelven valores por defecto en vez de lanzar, lo que basta para probar `update`.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // El visualizador solo depende de :core (modelos y tema). Nunca de :player ni de Media3.
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // El paquete `analysis` es Kotlin puro: se prueba con JUnit sin Robolectric.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
