package com.aprireader.app.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.prefs.DesignStyle
import com.aprireader.app.data.prefs.ThemeMode

/** Акцент, действующий прямо сейчас, и его происхождение — нужно UI для объяснения выбора. */
data class AccentState(
    val color: Color,
    val source: Source,
) {
    enum class Source { BRAND, GLOBAL_USER, BOOK_PINNED, COVER, SYSTEM }
}

val LocalAccent = staticCompositionLocalOf { AccentState(ApriBrassSeed, AccentState.Source.BRAND) }

val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.LIQUID_GLASS }

/** Признак «система просит уменьшить анимации» — уважается всеми переходами приложения. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ApriTheme(
    settings: AppSettings,
    /** Акцент книги, если пользователь сейчас в контексте конкретной книги. */
    bookAccent: Int? = null,
    bookAccentPinned: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val isAmoled = settings.themeMode == ThemeMode.AMOLED || (darkTheme && settings.pureBlackDark)
    val context = LocalContext.current

    val accentState = resolveAccent(settings, bookAccent, bookAccentPinned)

    // Смена акцента при открытии книги — это переход, а не мигание.
    val reduceMotion = settings.reduceMotion || !areAnimationsEnabled(context)
    val animatedAccent by animateColorAsState(
        targetValue = accentState.color,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 450),
        label = "accent",
    )

    val scheme: ColorScheme = when {
        settings.systemDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isAmoled ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> buildColorScheme(animatedAccent, darkTheme, isAmoled, settings.designStyle)
    }

    CompositionLocalProvider(
        LocalAccent provides accentState.copy(color = animatedAccent),
        LocalDesignStyle provides settings.designStyle,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ApriTypography,
            shapes = ApriShapes,
            content = content,
        )
    }
}

/**
 * Приоритет источников акцента.
 *
 * Закреплённый пользователем цвет книги — самый сильный: обещание «мой выбор не
 * перезапишется автоматикой» должно выполняться буквально. Дальше идёт цвет с
 * обложки, затем глобальный пользовательский, затем фирменный.
 */
private fun resolveAccent(
    settings: AppSettings,
    bookAccent: Int?,
    bookAccentPinned: Boolean,
): AccentState = when {
    bookAccent != null && bookAccentPinned -> AccentState(Color(bookAccent), AccentState.Source.BOOK_PINNED)
    bookAccent != null && settings.dynamicCoverTheming -> AccentState(Color(bookAccent), AccentState.Source.COVER)
    settings.globalAccent != null -> AccentState(Color(settings.globalAccent), AccentState.Source.GLOBAL_USER)
    else -> AccentState(ApriBrassSeed, AccentState.Source.BRAND)
}

private fun areAnimationsEnabled(context: android.content.Context): Boolean {
    val scale = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale > 0f
}
