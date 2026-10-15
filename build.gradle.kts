// Archivo raíz: solo declara los plugins para que los módulos los apliquen.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Hilt 2.59+ exige AGP 9. Mientras el proyecto use AGP 8.x, todas las librerías de
// com.google.dagger deben quedarse en la misma versión que el plugin (2.58); de lo
// contrario una dependencia transitiva (p. ej. androidx.hilt) subiría el runtime a 2.60
// y el procesador de anotaciones y el runtime dejarían de coincidir.
val hiltVersion = libs.versions.hilt.get()
subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.google.dagger") {
                useVersion(hiltVersion)
                because("alineado con el plugin de Hilt compatible con AGP 8.x")
            }
        }
    }
}
