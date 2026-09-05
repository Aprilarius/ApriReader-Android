package com.aprireader.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.aprireader.app.R
import com.aprireader.app.data.prefs.ReadingFont
import com.aprireader.app.data.prefs.TypographySettings
import java.io.File

/**
 * Два слоя типографики разведены сознательно.
 *
 * [UiFontFamily] — шрифт интерфейса: полка, настройки, статистика. Он постоянен,
 * пользователь его не меняет, и его задача — не мешать.
 * Шрифты чтения выбираются пользователем и живут только на странице книги.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(
    resId = resId,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * Шрифт заголовков интерфейса.
 *
 * Bricolage Grotesque выбран за характер: переменные вес и ширина, слегка
 * неровные пропорции — заголовки перестают выглядеть системными, но остаются
 * спокойными рядом с книжным текстом. В тексте книги он не участвует никогда:
 * слои чтения и интерфейса разведены намеренно.
 */
val DisplayFontFamily = FontFamily(
    variableFont(R.font.bricolage_grotesque_variable, FontWeight.Normal),
    variableFont(R.font.bricolage_grotesque_variable, FontWeight.Medium),
    variableFont(R.font.bricolage_grotesque_variable, FontWeight.SemiBold),
    variableFont(R.font.bricolage_grotesque_variable, FontWeight.Bold),
)

val UiFontFamily = FontFamily(
    variableFont(R.font.inter_variable, FontWeight.Light),
    variableFont(R.font.inter_variable, FontWeight.Normal),
    variableFont(R.font.inter_variable, FontWeight.Medium),
    variableFont(R.font.inter_variable, FontWeight.SemiBold),
    variableFont(R.font.inter_variable, FontWeight.Bold),
)

val LiterataFamily = FontFamily(
    variableFont(R.font.literata_variable, FontWeight.Light),
    variableFont(R.font.literata_variable, FontWeight.Normal),
    variableFont(R.font.literata_variable, FontWeight.Medium),
    variableFont(R.font.literata_variable, FontWeight.SemiBold),
    variableFont(R.font.literata_variable, FontWeight.Bold),
    variableFont(R.font.literata_italic_variable, FontWeight.Normal, FontStyle.Italic),
    variableFont(R.font.literata_italic_variable, FontWeight.Bold, FontStyle.Italic),
)

val LoraFamily = FontFamily(
    variableFont(R.font.lora_variable, FontWeight.Normal),
    variableFont(R.font.lora_variable, FontWeight.Medium),
    variableFont(R.font.lora_variable, FontWeight.SemiBold),
    variableFont(R.font.lora_variable, FontWeight.Bold),
)

val MerriweatherFamily = FontFamily(
    Font(R.font.merriweather_regular, FontWeight.Normal),
    Font(R.font.merriweather_regular, FontWeight.Bold),
)

val PtSerifFamily = FontFamily(
    Font(R.font.pt_serif_regular, FontWeight.Normal),
    Font(R.font.pt_serif_bold, FontWeight.Bold),
)

val SpectralFamily = FontFamily(
    variableFont(R.font.spectral_variable, FontWeight.Normal),
    variableFont(R.font.spectral_variable, FontWeight.Medium),
    variableFont(R.font.spectral_variable, FontWeight.SemiBold),
    variableFont(R.font.spectral_variable, FontWeight.Bold),
)

val EbGaramondFamily = FontFamily(
    variableFont(R.font.eb_garamond_variable, FontWeight.Normal),
    variableFont(R.font.eb_garamond_variable, FontWeight.Medium),
    variableFont(R.font.eb_garamond_variable, FontWeight.SemiBold),
    variableFont(R.font.eb_garamond_variable, FontWeight.Bold),
)

val SourceSerifFamily = FontFamily(
    variableFont(R.font.source_serif_variable, FontWeight.Normal),
    variableFont(R.font.source_serif_variable, FontWeight.Medium),
    variableFont(R.font.source_serif_variable, FontWeight.SemiBold),
    variableFont(R.font.source_serif_variable, FontWeight.Bold),
)

val BitterFamily = FontFamily(
    variableFont(R.font.bitter_variable, FontWeight.Normal),
    variableFont(R.font.bitter_variable, FontWeight.Medium),
    variableFont(R.font.bitter_variable, FontWeight.SemiBold),
    variableFont(R.font.bitter_variable, FontWeight.Bold),
)

val AlegreyaFamily = FontFamily(
    variableFont(R.font.alegreya_variable, FontWeight.Normal),
    variableFont(R.font.alegreya_variable, FontWeight.Medium),
    variableFont(R.font.alegreya_variable, FontWeight.SemiBold),
    variableFont(R.font.alegreya_variable, FontWeight.Bold),
)

val VollkornFamily = FontFamily(
    variableFont(R.font.vollkorn_variable, FontWeight.Normal),
    variableFont(R.font.vollkorn_variable, FontWeight.Medium),
    variableFont(R.font.vollkorn_variable, FontWeight.SemiBold),
    variableFont(R.font.vollkorn_variable, FontWeight.Bold),
)

val NotoSerifFamily = FontFamily(
    variableFont(R.font.noto_serif_variable, FontWeight.Normal),
    variableFont(R.font.noto_serif_variable, FontWeight.Medium),
    variableFont(R.font.noto_serif_variable, FontWeight.SemiBold),
    variableFont(R.font.noto_serif_variable, FontWeight.Bold),
)

val CormorantFamily = FontFamily(
    variableFont(R.font.cormorant_variable, FontWeight.Normal),
    variableFont(R.font.cormorant_variable, FontWeight.Medium),
    variableFont(R.font.cormorant_variable, FontWeight.SemiBold),
    variableFont(R.font.cormorant_variable, FontWeight.Bold),
)

val AtkinsonFamily = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

val LexendFamily = FontFamily(
    variableFont(R.font.lexend_variable, FontWeight.Light),
    variableFont(R.font.lexend_variable, FontWeight.Normal),
    variableFont(R.font.lexend_variable, FontWeight.Medium),
    variableFont(R.font.lexend_variable, FontWeight.SemiBold),
    variableFont(R.font.lexend_variable, FontWeight.Bold),
)

val FiraSansFamily = FontFamily(
    Font(R.font.fira_sans_regular, FontWeight.Normal),
    Font(R.font.fira_sans_regular, FontWeight.Bold),
)

/** Гарнитура для чтения по выбору пользователя. */
fun ReadingFont.family(): FontFamily = when (this) {
    ReadingFont.LITERATA -> LiterataFamily
    ReadingFont.LORA -> LoraFamily
    ReadingFont.MERRIWEATHER -> MerriweatherFamily
    ReadingFont.PT_SERIF -> PtSerifFamily
    ReadingFont.EB_GARAMOND -> EbGaramondFamily
    ReadingFont.SPECTRAL -> SpectralFamily
    ReadingFont.SOURCE_SERIF -> SourceSerifFamily
    ReadingFont.BITTER -> BitterFamily
    ReadingFont.ALEGREYA -> AlegreyaFamily
    ReadingFont.VOLLKORN -> VollkornFamily
    ReadingFont.NOTO_SERIF -> NotoSerifFamily
    ReadingFont.CORMORANT -> CormorantFamily
    ReadingFont.ATKINSON -> AtkinsonFamily
    ReadingFont.LEXEND -> LexendFamily
    ReadingFont.INTER -> UiFontFamily
    ReadingFont.FIRA_SANS -> FiraSansFamily
    ReadingFont.SYSTEM_SERIF -> FontFamily.Serif
    ReadingFont.SYSTEM_SANS -> FontFamily.SansSerif
    ReadingFont.MONO -> FontFamily.Monospace
    ReadingFont.CUSTOM -> LiterataFamily
}

/** Идентификатор ресурса короткого описания гарнитуры. */
val ReadingFont.hintRes: Int
    get() = when (this) {
        ReadingFont.LITERATA -> R.string.font_hint_literata
        ReadingFont.LORA -> R.string.font_hint_lora
        ReadingFont.MERRIWEATHER -> R.string.font_hint_merriweather
        ReadingFont.PT_SERIF -> R.string.font_hint_pt_serif
        ReadingFont.EB_GARAMOND -> R.string.font_hint_eb_garamond
        ReadingFont.SPECTRAL -> R.string.font_hint_spectral
        ReadingFont.SOURCE_SERIF -> R.string.font_hint_source_serif
        ReadingFont.BITTER -> R.string.font_hint_bitter
        ReadingFont.ALEGREYA -> R.string.font_hint_alegreya
        ReadingFont.VOLLKORN -> R.string.font_hint_vollkorn
        ReadingFont.NOTO_SERIF -> R.string.font_hint_noto_serif
        ReadingFont.CORMORANT -> R.string.font_hint_cormorant
        ReadingFont.ATKINSON -> R.string.font_hint_atkinson
        ReadingFont.LEXEND -> R.string.font_hint_lexend
        ReadingFont.INTER -> R.string.font_hint_inter
        ReadingFont.FIRA_SANS -> R.string.font_hint_fira_sans
        ReadingFont.SYSTEM_SERIF -> R.string.font_hint_system_serif
        ReadingFont.SYSTEM_SANS -> R.string.font_hint_system_sans
        ReadingFont.MONO -> R.string.font_hint_mono
        ReadingFont.CUSTOM -> R.string.font_hint_custom
    }

/** Разрешение гарнитуры с поддержкой пользовательских импортированных шрифтов */
fun resolveReadingFont(typography: TypographySettings): FontFamily {
    if (typography.font == ReadingFont.CUSTOM && !typography.customFontPath.isNullOrBlank()) {
        val file = File(typography.customFontPath)
        if (file.exists() && file.length() > 0) {
            val customFamily = runCatching { FontFamily(Font(file)) }.getOrNull()
            if (customFamily != null) return customFamily
        }
    }
    return typography.font.family()
}

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val ApriTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    titleMedium = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    titleSmall = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    bodyLarge = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    bodyMedium = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    bodySmall = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    labelLarge = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    labelMedium = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    labelSmall = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 13.sp,
        lineHeightStyle = lineHeightStyle,
    ),
)
