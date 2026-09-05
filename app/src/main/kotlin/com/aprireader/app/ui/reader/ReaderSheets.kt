package com.aprireader.app.ui.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aprireader.app.R
import com.aprireader.app.data.fonts.CustomFont
import com.aprireader.app.data.prefs.ReaderPageStyle
import com.aprireader.app.data.prefs.ReaderSettings
import com.aprireader.app.data.prefs.ReadingFont
import com.aprireader.app.data.prefs.TypographySettings
import com.aprireader.app.ui.theme.AccentPresets
import com.aprireader.app.ui.theme.SquircleSheet
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.theme.family
import com.aprireader.app.ui.theme.hintRes
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Лист оформления: типографика, страница, акцент, режимы фокуса.
 *
 * Все изменения применяются мгновенно и к живому тексту за листом — настраивать
 * чтение вслепую, по названиям параметров, неудобно.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypographySheet(
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onTypographyChange: ((TypographySettings) -> TypographySettings) -> Unit,
    onReaderChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onPinAccent: (Int) -> Unit,
    onUnpinAccent: () -> Unit,
    onBionicIntensity: (Float) -> Unit,
    onToggleBionic: () -> Unit,
    presets: List<com.aprireader.app.data.db.TypographyPresetEntity> = emptyList(),
    customFonts: List<CustomFont> = emptyList(),
    onApplyPreset: (com.aprireader.app.data.db.TypographyPresetEntity) -> Unit = {},
    onSavePreset: (String) -> Unit = {},
    onDeletePreset: (Long) -> Unit = {},
    onImportFont: ((android.net.Uri, android.content.ContentResolver) -> Unit)? = null,
    onDeleteCustomFont: ((String) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val typography = state.typography

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SquircleSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            if (presets.isNotEmpty()) {
                SheetTitle(stringResource(R.string.reader_presets_title))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = { onApplyPreset(preset) },
                            label = { Text(preset.name) },
                            shape = SquircleSm,
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeletePreset(preset.id) },
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            var presetName by remember { mutableStateOf("") }
            var savingPreset by remember { mutableStateOf(false) }
            if (savingPreset) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text(stringResource(R.string.reader_preset_name_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        onSavePreset(presetName)
                        presetName = ""
                        savingPreset = false
                    }) { Text(stringResource(R.string.action_save)) }
                }
            } else {
                TextButton(onClick = { savingPreset = true }) {
                    Text(stringResource(R.string.reader_preset_save_current))
                }
            }

            val context = LocalContext.current
            val fontPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let {
                    onImportFont?.invoke(it, context.contentResolver)
                }
            }

            SheetTitle(stringResource(R.string.reader_font_title))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadingFont.entries.filter { it != ReadingFont.CUSTOM }.forEach { font ->
                    FilterChip(
                        selected = typography.font == font,
                        onClick = {
                            onTypographyChange {
                                it.copy(font = font, customFontPath = null, customFontName = null)
                            }
                        },
                        label = {
                            Text(
                                text = font.displayName,
                                fontFamily = font.family(),
                            )
                        },
                        shape = SquircleSm,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(typography.font.hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            SheetTitle(stringResource(R.string.reader_font_custom_section))
            if (customFonts.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    customFonts.forEach { cf ->
                        val isSelected = typography.font == ReadingFont.CUSTOM && typography.customFontPath == cf.filePath
                        val customFamily = remember(cf.filePath) {
                            runCatching { FontFamily(Font(File(cf.filePath))) }.getOrNull() ?: FontFamily.Default
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onTypographyChange {
                                    it.copy(
                                        font = ReadingFont.CUSTOM,
                                        customFontPath = cf.filePath,
                                        customFontName = cf.name,
                                    )
                                }
                            },
                            label = {
                                Text(text = cf.name, fontFamily = customFamily)
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.reader_font_delete_confirm),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteCustomFont?.invoke(cf.id) },
                                )
                            },
                            shape = SquircleSm,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    fontPickerLauncher.launch(
                        arrayOf(
                            "font/*",
                            "application/octet-stream",
                            "application/x-font-ttf",
                            "application/x-font-otf",
                            "*/*",
                        )
                    )
                },
                shape = SquircleSm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reader_font_import_action))
            }

            SheetSlider(
                label = stringResource(R.string.reader_font_size),
                value = typography.fontSizeSp,
                range = 12f..34f,
                valueLabel = "${typography.fontSizeSp.roundToInt()} sp",
                onChange = { onTypographyChange { current -> current.copy(fontSizeSp = it) } },
            )
            SheetSlider(
                label = stringResource(R.string.reader_line_height),
                value = typography.lineHeight,
                range = 1.1f..2.2f,
                valueLabel = String.format(Locale.getDefault(), "%.2f", typography.lineHeight),
                onChange = { onTypographyChange { current -> current.copy(lineHeight = it) } },
            )
            SheetSlider(
                label = stringResource(R.string.reader_letter_spacing),
                value = typography.letterSpacing,
                range = -0.02f..0.12f,
                valueLabel = String.format(Locale.getDefault(), "%.3f em", typography.letterSpacing),
                onChange = { onTypographyChange { current -> current.copy(letterSpacing = it) } },
            )
            SheetSlider(
                label = stringResource(R.string.reader_paragraph_spacing),
                value = typography.paragraphSpacing,
                range = 0f..1.6f,
                valueLabel = String.format(Locale.getDefault(), "%.1f", typography.paragraphSpacing),
                onChange = { onTypographyChange { current -> current.copy(paragraphSpacing = it) } },
            )
            SheetSlider(
                label = stringResource(R.string.reader_margins),
                value = typography.horizontalMarginDp,
                range = 8f..64f,
                valueLabel = "${typography.horizontalMarginDp.roundToInt()} dp",
                onChange = { onTypographyChange { current -> current.copy(horizontalMarginDp = it) } },
            )
            SheetSlider(
                label = stringResource(R.string.reader_column_width),
                value = typography.maxLineWidthChars.toFloat(),
                range = 40f..110f,
                valueLabel = stringResource(R.string.reader_column_chars, typography.maxLineWidthChars),
                onChange = { value -> onTypographyChange { it.copy(maxLineWidthChars = value.roundToInt()) } },
            )

            SheetSwitch(stringResource(R.string.reader_justify), typography.justify) { value ->
                onTypographyChange { it.copy(justify = value) }
            }
            SheetSwitch(stringResource(R.string.reader_first_line_indent), typography.firstLineIndent) { value ->
                onTypographyChange { it.copy(firstLineIndent = value) }
            }
            SheetSwitch(stringResource(R.string.reader_hyphenation), typography.hyphenation) { value ->
                onTypographyChange { it.copy(hyphenation = value) }
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))

            SheetTitle(stringResource(R.string.reader_page_style_title))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pageStyles.forEach { (style, labelRes) ->
                    FilterChip(
                        selected = state.pageStyle == style,
                        onClick = { onReaderChange { it.copy(pageStyle = style) } },
                        label = { Text(stringResource(labelRes)) },
                        shape = SquircleSm,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))

            SheetTitle(stringResource(R.string.details_accent_section))
            Text(
                text = if (state.accentPinned) {
                    stringResource(R.string.details_accent_pinned)
                } else {
                    stringResource(R.string.details_accent_auto)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (preset in AccentPresets) {
                    AccentDot(
                        color = preset.color,
                        selected = state.accentPinned && state.accent == preset.color.toArgb(),
                        name = stringResource(preset.nameRes),
                        onClick = { onPinAccent(preset.color.toArgb()) },
                    )
                }
            }
            if (state.accentPinned) {
                TextButton(onClick = onUnpinAccent) {
                    Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.details_accent_reset))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))

            SheetTitle(stringResource(R.string.reader_scroll_mode_title))
            val scrollModes = listOf(
                com.aprireader.app.data.prefs.ReadingScrollMode.CONTINUOUS_VERTICAL to R.string.reader_scroll_continuous_vertical,
                com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL to R.string.reader_scroll_paged_horizontal,
                com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_VERTICAL to R.string.reader_scroll_paged_vertical,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scrollModes.forEach { (mode, labelRes) ->
                    FilterChip(
                        selected = state.settings.reader.scrollMode == mode,
                        onClick = {
                            onReaderChange {
                                it.copy(
                                    scrollMode = mode,
                                    horizontalPaging = mode == com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL
                                )
                            }
                        },
                        label = { Text(stringResource(labelRes)) },
                        shape = SquircleSm,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SheetSwitch(
                stringResource(R.string.reader_continuous_reading_title),
                state.settings.reader.continuousReading
            ) { value ->
                onReaderChange { it.copy(continuousReading = value) }
            }
            SheetSwitch(
                stringResource(R.string.reader_tap_zones_title),
                state.settings.reader.tapZonesPaging
            ) { value ->
                onReaderChange { it.copy(tapZonesPaging = value) }
            }

            HorizontalDivider(Modifier.padding(vertical = 18.dp))

            SheetTitle(stringResource(R.string.reader_controls_title))
            SheetSwitch(stringResource(R.string.reader_fullscreen), state.settings.reader.fullscreen) { value ->
                onReaderChange { it.copy(fullscreen = value) }
            }
            SheetSwitch(stringResource(R.string.reader_tap_paging), state.settings.reader.horizontalPaging) { value ->
                onReaderChange { it.copy(horizontalPaging = value) }
            }
            SheetSwitch(stringResource(R.string.reader_volume_paging), state.settings.reader.volumeKeysPaging) { value ->
                onReaderChange { it.copy(volumeKeysPaging = value) }
            }
            SheetSwitch(stringResource(R.string.reader_progress_bar), state.settings.reader.showProgressBar) { value ->
                onReaderChange { it.copy(showProgressBar = value) }
            }
            SheetSwitch(stringResource(R.string.reader_keep_screen_on), state.settings.reader.keepScreenOn) { value ->
                onReaderChange { it.copy(keepScreenOn = value) }
            }

            if (state.supportsTextModes) {
                HorizontalDivider(Modifier.padding(vertical = 18.dp))
                SheetTitle(stringResource(R.string.reader_focus_title))
                SheetSwitch(stringResource(R.string.reader_bionic_reading), state.focus.bionicEnabled) { onToggleBionic() }
                if (state.focus.bionicEnabled) {
                    SheetSlider(
                        label = stringResource(R.string.reader_bionic_intensity),
                        value = state.focus.bionicIntensity,
                        range = 0.25f..0.7f,
                        valueLabel = "${(state.focus.bionicIntensity * 100).roundToInt()}%",
                        onChange = onBionicIntensity,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSheet(
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SquircleSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SheetTitle(stringResource(R.string.reader_toc_title))
        }
        if (state.chapters.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 560.dp)) {
                items(state.chapters, key = { it.index }) { chapter ->
                    val selected = chapter.index == state.chapterIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, onClick = { onSelectChapter(chapter.index) })
                            .padding(
                                start = 20.dp + (chapter.depth * 14).dp,
                                end = 20.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = chapter.title ?: stringResource(R.string.reader_chapter_of, chapter.index + 1, state.chapters.size),
                            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 560.dp)) {
                items((0 until state.pageCount).toList()) { page ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPage(page) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.reader_page_of, page + 1, state.pageCount), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
}

@Composable
private fun SheetSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun SheetSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = com.aprireader.app.ui.theme.onColorFor(color),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val pageStyles = listOf(
    ReaderPageStyle.FOLLOW_THEME to R.string.reader_style_follow_theme,
    ReaderPageStyle.PAPER to R.string.reader_style_paper,
    ReaderPageStyle.SEPIA to R.string.reader_style_sepia,
    ReaderPageStyle.GRAPHITE to R.string.reader_style_graphite,
    ReaderPageStyle.BLACK to R.string.reader_style_black,
)
