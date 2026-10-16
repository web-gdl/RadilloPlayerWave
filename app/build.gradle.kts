plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.radillo.playerwave"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.radillo.playerwave"
        minSdk = 24
        targetSdk = 37
        versionCode = 6
        versionName = "0.6.0-faseA"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Solo los idiomas de la app: fuera las traducciones de las bibliotecas AndroidX (menos APK).
    androidResources {
        localeFilters += listOf("es", "en")
    }

    // Clave de firma: en CI se descifra keystore/radillo-release.jks.enc con el secreto
    // RADILLO_KEYSTORE_PASSWORD y se exporta RADILLO_KEYSTORE_PATH. Sin ella (build local o
    // secreto ausente) el release se firma con la clave de depuración, para seguir siendo instalable.
    val releaseKeystore = System.getenv("RADILLO_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RADILLO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RADILLO_KEY_ALIAS") ?: "radillo"
                keyPassword = System.getenv("RADILLO_KEY_PASSWORD") ?: System.getenv("RADILLO_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true          // R8 en modo completo (por defecto desde AGP 8)
            isShrinkResources = true        // elimina recursos sin referencia
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":player"))
    implementation(project(":visualizer"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)


    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
