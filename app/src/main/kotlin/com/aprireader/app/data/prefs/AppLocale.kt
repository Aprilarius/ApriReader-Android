package com.aprireader.app.data.prefs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * Языки интерфейса.
 *
 * Список закрытый и задан продуктом, а не системой: переводы существуют только
 * для этих пяти языков, и предлагать остальные было бы обманом.
 */
enum class AppLanguage(val tag: String, val endonym: String) {
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English"),
    ITALIAN("it", "Italiano"),
    AZERBAIJANI("az", "Azərbaycan"),
    GERMAN("de", "Deutsch");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** Что предложить по умолчанию: язык системы, если он нам знаком, иначе английский. */
        fun suggested(context: Context): AppLanguage {
            val systemTag = runCatching { context.resources.configuration.locales[0].language }.getOrNull()
            return fromTag(systemTag) ?: ENGLISH
        }
    }
}

/**
 * Хранилище выбранного языка поверх SharedPreferences.
 *
 * Настройки живут в DataStore, но язык нужен синхронно — в `attachBaseContext`,
 * до первого кадра и до запуска корутин. Читать оттуда DataStore нельзя, поэтому
 * язык дублируется сюда: DataStore остаётся источником правды для UI, а эта
 * копия отвечает только за подмену конфигурации при старте.
 */
object LocaleStore {

    private const val PREFS = "apri_locale"
    private const val KEY_TAG = "language_tag"

    fun read(context: Context): String? = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
    }.getOrNull()

    fun write(context: Context, tag: String?) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .apply { if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag) }
                .apply()
        }
    }

    /** Возвращает контекст с выбранным языком; если выбора не было — исходный. */
    fun wrap(context: Context): Context {
        val tag = read(context)?.takeIf { it.isNotBlank() } ?: return context
        return runCatching {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLocales(LocaleList(locale))
            context.createConfigurationContext(configuration)
        }.getOrDefault(context)
    }
}

/** Контекст с указанным языком — для мгновенного переключения без перезапуска экрана. */
fun localizedContext(context: Context, tag: String?): Context {
    if (tag.isNullOrBlank()) return context
    return runCatching {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        val configContext = context.createConfigurationContext(configuration)
        object : ContextWrapper(context) {
            override fun getResources(): Resources = configContext.resources
            override fun getAssets(): AssetManager = configContext.assets
        }
    }.getOrDefault(context)
}

/** Вспомогательная функция для безопасного поиска Activity из любого контекста или обёртки. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
