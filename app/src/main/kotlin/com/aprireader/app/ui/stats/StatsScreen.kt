package com.aprireader.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprireader.app.R
import com.aprireader.app.data.profile.ReaderTitle
import com.aprireader.app.domain.AchievementProgress
import com.aprireader.app.ui.common.GlassPanel
import com.aprireader.app.ui.components.UserAvatar
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.theme.SquircleXs
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Статистика чтения и достижения.
 *
 * Всё, что здесь показано, посчитано на устройстве из локальной базы. Никакие
 * данные о том, что и сколько человек читает, наружу не уходят.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 40.dp,
            ),
        ) {
            item {
                val activeTitle = ReaderTitle.fromKey(state.userTitleKey)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp)
                        .clip(SquircleMd)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), SquircleMd)
                        .padding(14.dp),
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        UserAvatar(
                            avatarId = state.userAvatarId,
                            customAvatarPath = state.customAvatarPath,
                            modifier = Modifier.size(56.dp),
                            shape = SquircleLg,
                            borderWidth = 2.dp,
                            borderColor = MaterialTheme.colorScheme.primary,
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(SquircleSm)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.surface, SquircleSm),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = activeTitle.badgeIcon, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val displayName = if (state.userName.isNotBlank()) state.userName else stringResource(R.string.app_name)
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(activeTitle.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.userBio.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "\"${state.userBio}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        value = formatMinutes(state.totalMinutes),
                        label = stringResource(R.string.stats_total_reading),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        value = "${state.streakDays}",
                        label = if (state.streakDays == 1) stringResource(R.string.stats_streak_day) else stringResource(R.string.stats_streak_days),
                        icon = Icons.Rounded.LocalFireDepartment,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        value = formatMinutes(state.todayMinutes),
                        label = stringResource(R.string.stats_today),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        value = "${state.finishedBooks}",
                        label = stringResource(R.string.stats_finished_books),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SectionTitle(stringResource(R.string.stats_last_week))
                WeekChart(state.week)
            }

            item {
                SectionTitle(stringResource(R.string.stats_achievements_header, state.unlockedCount, state.achievements.size))
            }

            items(state.achievements, key = { it.achievement.key }) { progress ->
                AchievementRow(progress)
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    GlassPanel(
        modifier = modifier.semantics { contentDescription = "$value $label" },
        shape = SquircleMd,
    ) {
        Column(Modifier.padding(16.dp)) {
            icon?.let {
                Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Компактный столбчатый график: семь дней, без осей и лишней сетки. */
@Composable
private fun WeekChart(week: List<DayActivity>) {
    val maxMinutes = (week.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(30)

    Row(
        Modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { day ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (day.minutes > 0) "${day.minutes}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val fraction = (day.minutes.toFloat() / maxMinutes).coerceIn(0.02f, 1f)
                val barDescription = "${day.date}: " + stringResource(R.string.stats_minutes_short, day.minutes)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((90 * fraction).dp)
                        .clip(SquircleXs)
                        .background(
                            if (day.minutes > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .semantics {
                            contentDescription = barDescription
                        },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = day.date.dayOfWeek
                        .getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
                        .take(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AchievementRow(progress: AchievementProgress) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(SquircleSm)
                .background(
                    if (progress.unlocked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = if (progress.unlocked) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(progress.achievement.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (progress.unlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = stringResource(progress.achievement.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!progress.unlocked) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
    )
}

@Composable
internal fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.stats_minutes_short, minutes)
    minutes % 60 == 0 -> stringResource(R.string.stats_hours_short, minutes / 60)
    else -> stringResource(R.string.stats_hours_minutes_short, minutes / 60, minutes % 60)
}
