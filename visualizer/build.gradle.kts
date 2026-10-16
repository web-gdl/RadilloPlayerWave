plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.radillo.playerwave.visualizer"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Las escenas usan Paint, Path, Canvas y Shader; en la JVM los stubs del SDK devuelven
        // valores por defecto en vez de lanzar, lo que basta para probar `update` y `draw`.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // El visualizador dibuja con android.graphics (sin Compose) y solo depende de :core
    // (perfil del dispositivo). Nunca de :player ni de Media3.
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)

    // El paquete `analysis` es Kotlin puro: se prueba con JUnit sin Robolectric.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
