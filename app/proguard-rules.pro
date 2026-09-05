# ApriReader — правила R8.
#
# Приложение не использует рефлексию само, но три зависимости требуют внимания:
# PDFBox (загружает ресурсы и классы по имени), junrar (внутренние заголовки)
# и Room (генерирует реализации DAO).

# --- Room ---
-keep class androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- PDFBox для Android ---
# Библиотека обращается к ресурсам шрифтов и классам PDF-моделей по имени.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn org.apache.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**
-dontwarn javax.xml.bind.**

# --- junrar ---
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**
-dontwarn org.slf4j.**

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Kotlin / корутины ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }

# --- Модель книг ---
# Модель формата разбирается только своим кодом, но имена классов участвуют в
# сообщениях об ошибках парсинга — оставляем их читаемыми.
-keepnames class com.aprireader.bookformat.model.** { *; }

# Compose не требует особых правил, но оставляем имена composable-функций для
# читаемых стектрейсов при отладке релизной сборки.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Domain and Data Models ---
-keep class com.aprireader.app.domain.** { *; }
-keep class com.aprireader.app.data.db.** { *; }
-keep class com.aprireader.app.data.metadata.** { *; }
-keep class com.aprireader.app.data.profile.** { *; }
-keep class com.aprireader.app.data.prefs.** { *; }

# --- Coil ---
-dontwarn coil.**

# --- Media3 ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

