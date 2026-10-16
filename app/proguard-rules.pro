# Reglas R8 del módulo app.
# Media3, Hilt, Room y Coil incluyen sus propias reglas consumer; no hace falta duplicarlas.

# Más agresivo: R8 puede cambiar visibilidades y aplanar paquetes (menos dex, menos clases).
-allowaccessmodification
-repackageclasses ''

# Fuera los logs de depuración en release: cada llamada ahorra trabajo a una CPU lenta.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Conserva nombres de archivo y líneas para trazas legibles en release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
