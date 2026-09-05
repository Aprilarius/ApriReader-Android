package com.aprireader.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.aprireader.app.data.prefs.localizedContext

/**
 * Подменяет язык для всего поддерева интерфейса.
 *
 * Благодаря этому смена языка применяется мгновенно и не требует пересоздания
 * активити: на экране приветствия текст меняется прямо под пальцем, а
 * состояние шага не теряется.
 */
@Composable
fun LocalizedContent(languageTag: String?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val localized = remember(languageTag, context) { localizedContext(context, languageTag) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
    ) {
        content()
    }
}

/**
 * Обертка для строк: позволяет ViewModel отдавать как локализованные ресурсы,
 * так и динамические строки от ошибок/системы.
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    data class StringResource(val resId: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(resId: Int, vararg args: Any) : this(resId, args.toList())
    }

    fun asString(context: android.content.Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(resId, *args.toTypedArray())
    }
}

@Composable
fun UiText.asString(): String = asString(LocalContext.current)
