package com.aprireader.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aprireader.app.MainActivity
import com.aprireader.app.appContainer

/**
 * Виджет «текущая книга»: обложка не нужна — нужен ответ на вопрос
 * «что я читаю и сколько осталось», одним касанием возвращающий в книгу.
 */
class CurrentBookWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val book = runCatching { context.appContainer.library.mostRecentBook() }.getOrNull()

        provideContent {
            GlanceTheme {
                Content(
                    title = book?.title ?: "Библиотека пуста",
                    author = book?.authorLine.orEmpty(),
                    progress = book?.progress ?: 0f,
                    bookId = book?.id,
                    context = context,
                )
            }
        }
    }

    @Composable
    private fun Content(
        title: String,
        author: String,
        progress: Float,
        bookId: String?,
        context: Context,
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (bookId != null) putExtra(EXTRA_BOOK_ID, bookId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(openIntent)),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = if (bookId != null) "Продолжить чтение" else "ApriReader",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = title,
                maxLines = 2,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (author.isNotBlank()) {
                Text(
                    text = author,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
            if (bookId != null) {
                Spacer(GlanceModifier.height(10.dp))
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth(),
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "com.aprireader.app.EXTRA_BOOK_ID"

        /** Вызывается после изменения прогресса, чтобы виджет не показывал вчерашние данные. */
        suspend fun refresh(context: Context) {
            runCatching { CurrentBookWidget().updateAll(context) }
        }
    }
}

class CurrentBookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrentBookWidget()
}

private val Int.sp: androidx.compose.ui.unit.TextUnit
    get() = androidx.compose.ui.unit.TextUnit(toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
