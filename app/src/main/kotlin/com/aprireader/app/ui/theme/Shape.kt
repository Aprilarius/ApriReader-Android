package com.aprireader.app.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable

/**
 * Формы Material 3, собранные на сглаженных углах — зависят от [LocalDesignStyle].
 *
 * Компоненты MD3 берут формы отсюда, поэтому диалоги, чипы, кнопки и листы,
 * которым не передали свою форму явно, получают геометрию текущего стиля без
 * правки каждого места вызова. Радиусы взяты крупнее стандартных: на 4–8 dp
 * сглаживание не читается, и вся затея теряет смысл.
 */
val ApriShapes: Shapes
    @Composable get() = Shapes(
        extraSmall = SquircleXs,
        small = SquircleSm,
        medium = SquircleMd,
        large = SquircleLg,
        extraLarge = SquircleXl,
    )

/** Форма обложки книги — одна на полку, карточку книги и переход в читалку. */
val CoverShape: SquircleShape
    @Composable get() = SquircleSm
