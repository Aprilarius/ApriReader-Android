package com.aprireader.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import com.aprireader.app.ui.theme.LocalReduceMotion

/**
 * Область общих элементов и текущий переход экрана.
 *
 * Передаются через CompositionLocal, чтобы обложке на полке не приходилось
 * протаскивать два scope через пять уровней композиции. Если анимации
 * отключены в системе — модификатор становится пустым, и переход просто не
 * происходит.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Единственный осмысленный motion-акцент приложения: обложка на полке → экран чтения. */
private val coverBounds = BoundsTransform { _, _ ->
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

fun Modifier.sharedCover(bookId: String, clipShape: Shape = RectangleShape): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current
    val reduceMotion = LocalReduceMotion.current
    if (sharedScope == null || animatedScope == null || reduceMotion) {
        this
    } else {
        with(sharedScope) {
            this@composed.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "cover-$bookId"),
                animatedVisibilityScope = animatedScope,
                boundsTransform = coverBounds,
                clipInOverlayDuringTransition = OverlayClip(clipShape),
            )
        }
    }
}

/** Общий элемент для произвольного содержимого (заголовок книги при переходе). */
fun Modifier.sharedKey(key: String): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current
    val reduceMotion = LocalReduceMotion.current
    if (sharedScope == null || animatedScope == null || reduceMotion) {
        this
    } else {
        with(sharedScope) {
            this@composed.sharedElement(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedScope,
                boundsTransform = coverBounds,
            )
        }
    }
}
