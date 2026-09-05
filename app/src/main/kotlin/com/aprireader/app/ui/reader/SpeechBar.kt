package com.aprireader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.aprireader.app.ui.common.GlassPanel
import com.aprireader.app.ui.theme.GlassLevel
import com.aprireader.app.ui.theme.SquircleLg
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aprireader.app.data.tts.TtsState
import com.aprireader.app.ui.theme.SquircleMd
import java.util.Locale

import androidx.compose.ui.res.stringResource
import com.aprireader.app.R

/**
 * Панель озвучивания. Появляется, когда чтение вслух запущено, и остаётся
 * доступной даже при скрытом управлении — иначе поставить на паузу нечем.
 */
@Composable
fun SpeechBar(
    tts: TtsState,
    chapterTitle: String?,
    onToggle: () -> Unit,
    onSkip: (Int) -> Unit,
    onRate: (Float) -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = SquircleLg,
        level = GlassLevel.Chrome,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val statusText = when {
                        tts.initializing -> stringResource(R.string.tts_speaking_title) + "…"
                        tts.speaking -> stringResource(R.string.tts_speaking_title)
                        else -> stringResource(R.string.reader_action_pause)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    chapterTitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    val hasError = tts.languageMissing || tts.errorMessage != null
                    if (hasError) {
                        Text(
                            text = tts.errorMessage ?: stringResource(R.string.tts_voice_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                        )
                    }
                }
                if (tts.languageMissing || tts.errorMessage != null) {
                    androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                        Text(
                            text = if (tts.languageMissing) "Установить" else stringResource(R.string.menu_settings),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tts_stop))
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSkip(-1) }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.tts_prev_paragraph))
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (tts.speaking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (tts.speaking) stringResource(R.string.reader_action_pause) else stringResource(R.string.reader_action_resume),
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = { onSkip(1) }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.tts_next_paragraph))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.getDefault(), "%.1f×", tts.rate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val ttsSpeedLabel = stringResource(R.string.tts_speed)
                Slider(
                    value = tts.rate,
                    onValueChange = onRate,
                    valueRange = 0.5f..2.5f,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = ttsSpeedLabel },
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
