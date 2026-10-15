// Archivo raíz: solo declara los plugins para que los módulos los apliquen.
// Con AGP 9 el soporte de Kotlin está integrado: no se aplica `org.jetbrains.kotlin.android`.
// La versión de Kotlin (KGP) la fija el plugin del compilador de Compose, que va a la par.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
