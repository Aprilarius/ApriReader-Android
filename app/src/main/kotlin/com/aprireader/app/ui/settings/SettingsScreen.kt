package com.aprireader.app.ui.settings

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprireader.app.R
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.prefs.ThemeMode
import com.aprireader.app.data.profile.ReaderTitle
import com.aprireader.app.domain.LibrarySource
import com.aprireader.app.ui.components.UserAvatar
import com.aprireader.app.ui.theme.AccentPresets
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.theme.family
import com.aprireader.app.ui.theme.hintRes
import com.aprireader.app.ui.theme.onColorFor
import java.io.File

/** Настройки приложения: внешний вид, библиотека, приватность. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle(emptyList())

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.importCustomFont(it, context.contentResolver) { success, fontName ->
                if (success) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.msg_font_imported, fontName ?: ""),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.err_font_import_failed),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    var profileEditorOpen by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::addFolder) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            com.aprireader.app.ui.theme.GlassmorphicBackdrop(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
            SettingsSection(stringResource(R.string.settings_profile_section))

            ProfileCard(
                settings = settings,
                onEditProfile = { profileEditorOpen = true },
            )

            if (profileEditorOpen) {
                ProfileEditorSheet(
                    settings = settings,
                    onDismiss = { profileEditorOpen = false },
                    onUpdateName = viewModel::setUserName,
                    onUpdateAvatarId = viewModel::setUserAvatarId,
                    onUpdateTitleKey = viewModel::setUserTitleKey,
                    onUpdateBio = viewModel::setUserBio,
                    onPickCustomAvatarUri = { uri ->
                        viewModel.saveCustomAvatar(uri, context.contentResolver)
                    },
                    onClearCustomAvatar = viewModel::clearCustomAvatar,
                )
            }

            Text(
                text = stringResource(R.string.settings_language_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.aprireader.app.data.prefs.AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = settings.languageTag == lang.tag,
                        onClick = {
                            viewModel.setLanguage(lang.tag)
                        },
                        label = { Text(lang.endonym) },
                        shape = SquircleSm,
                    )
                }
            }

            SettingsSection(stringResource(R.string.settings_shelf_section))
            Text(
                text = stringResource(R.string.shelf_scale_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    2 to R.string.shelf_scale_large,
                    3 to R.string.shelf_scale_standard,
                    4 to R.string.shelf_scale_compact,
                    5 to R.string.shelf_scale_mini,
                ).forEach { (cols, labelRes) ->
                    FilterChip(
                        selected = settings.shelfColumns == cols,
                        onClick = { viewModel.setShelfColumns(cols) },
                        label = { Text(stringResource(labelRes)) },
                        shape = SquircleSm,
                    )
                }
            }
            SettingsSwitch(
                title = stringResource(R.string.settings_shelf_3d_crease),
                subtitle = stringResource(R.string.settings_shelf_3d_crease_sub),
                checked = settings.shelf3dCrease,
                onChange = viewModel::setShelf3dCrease,
            )

            SettingsSection(stringResource(R.string.settings_reading_comfort_section))
            Text(
                text = stringResource(R.string.reader_font_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.aprireader.app.data.prefs.ReadingFont.entries.filter { it != com.aprireader.app.data.prefs.ReadingFont.CUSTOM }.forEach { font ->
                    FilterChip(
                        selected = settings.reader.typography.font == font,
                        onClick = { viewModel.setReadingFont(font) },
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(settings.reader.typography.font.hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (customFonts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.reader_font_custom_section),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    customFonts.forEach { cf ->
                        val isSelected = settings.reader.typography.font == com.aprireader.app.data.prefs.ReadingFont.CUSTOM &&
                            settings.reader.typography.customFontPath == cf.filePath
                        val customFamily = remember(cf.filePath) {
                            runCatching { FontFamily(Font(File(cf.filePath))) }.getOrNull() ?: FontFamily.Default
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setReadingFont(
                                    com.aprireader.app.data.prefs.ReadingFont.CUSTOM,
                                    cf.filePath,
                                    cf.name,
                                )
                            },
                            label = { Text(cf.name, fontFamily = customFamily) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.reader_font_delete_confirm),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.deleteCustomFont(cf.id) },
                                )
                            },
                            shape = SquircleSm,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.reader_scroll_mode_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scrollModes.forEach { (mode, labelRes) ->
                    FilterChip(
                        selected = settings.reader.scrollMode == mode,
                        onClick = { viewModel.setReadingScrollMode(mode) },
                        label = { Text(stringResource(labelRes)) },
                        shape = SquircleSm,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsSwitch(
                title = stringResource(R.string.reader_continuous_reading_title),
                subtitle = stringResource(R.string.reader_continuous_reading_desc),
                checked = settings.reader.continuousReading,
                onChange = viewModel::setContinuousReading,
            )
            SettingsSwitch(
                title = stringResource(R.string.reader_tap_zones_title),
                subtitle = stringResource(R.string.reader_tap_zones_desc),
                checked = settings.reader.tapZonesPaging,
                onChange = viewModel::setTapZonesPaging,
            )
            SettingsSwitch(
                title = stringResource(R.string.settings_keep_screen_on),
                subtitle = stringResource(R.string.settings_keep_screen_on_sub),
                checked = settings.reader.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
            SettingsSwitch(
                title = stringResource(R.string.settings_fullscreen),
                subtitle = stringResource(R.string.settings_fullscreen_sub),
                checked = settings.reader.fullscreen,
                onChange = viewModel::setFullscreen,
            )
            SettingsSwitch(
                title = stringResource(R.string.settings_volume_keys),
                subtitle = stringResource(R.string.settings_volume_keys_sub),
                checked = settings.reader.volumeKeysPaging,
                onChange = viewModel::setVolumeKeysPaging,
            )
            SettingsSwitch(
                title = stringResource(R.string.settings_progress_bar),
                subtitle = stringResource(R.string.settings_progress_bar_sub),
                checked = settings.reader.showProgressBar,
                onChange = viewModel::setShowProgressBar,
            )

            SettingsSection(stringResource(R.string.settings_tts_section))
            Text(
                text = stringResource(R.string.settings_tts_speed_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                    FilterChip(
                        selected = settings.ttsDefaultSpeed == s,
                        onClick = { viewModel.setTtsDefaultSpeed(s) },
                        label = { Text("${s}x") },
                        shape = SquircleSm,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.settings_tts_engine_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.ttsEnginePackage == null,
                    onClick = { viewModel.setTtsEngine(null) },
                    label = { Text(stringResource(R.string.settings_tts_engine_system)) },
                    shape = SquircleSm,
                )
                state.ttsEngines.forEach { engine ->
                    FilterChip(
                        selected = settings.ttsEnginePackage == engine.packageName,
                        onClick = { viewModel.setTtsEngine(engine.packageName) },
                        label = { Text(engine.label.ifBlank { engine.packageName }) },
                        shape = SquircleSm,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { viewModel.openSystemTtsSettings(context) }) {
                Text(stringResource(R.string.settings_tts_open_system))
            }
            Text(
                text = stringResource(R.string.settings_tts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SettingsSection(stringResource(R.string.settings_theme_section))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                themeModes.forEach { (mode, labelRes) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(stringResource(labelRes)) },
                        shape = SquircleSm,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.settings_design_style_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                designStyles.forEach { (style, labelRes) ->
                    FilterChip(
                        selected = settings.designStyle == style,
                        onClick = { viewModel.setDesignStyle(style) },
                        label = { Text(stringResource(labelRes)) },
                        shape = SquircleSm,
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_design_style_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            SettingsSwitch(
                title = stringResource(R.string.settings_pure_black),
                subtitle = stringResource(R.string.settings_pure_black_sub),
                checked = settings.pureBlackDark,
                onChange = viewModel::setPureBlack,
            )

            SettingsSection(stringResource(R.string.settings_color_section))
            SettingsSwitch(
                title = stringResource(R.string.settings_dynamic_cover),
                subtitle = stringResource(R.string.settings_dynamic_cover_sub),
                checked = settings.dynamicCoverTheming,
                onChange = viewModel::setDynamicCoverTheming,
            )
            SettingsSwitch(
                title = stringResource(R.string.settings_material_you),
                subtitle = stringResource(R.string.settings_material_you_sub),
                checked = settings.systemDynamicColor,
                onChange = viewModel::setSystemDynamicColor,
            )
            Text(
                text = stringResource(R.string.settings_default_accent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (preset in AccentPresets) {
                    val selected = settings.globalAccent == preset.color.toArgb()
                    val name = stringResource(preset.nameRes)
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(preset.color)
                            .clickable(
                                onClickLabel = name,
                                onClick = { viewModel.setGlobalAccent(preset.color.toArgb()) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = onColorFor(preset.color),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            if (settings.globalAccent != null) {
                TextButton(onClick = { viewModel.setGlobalAccent(null) }) {
                    Text(stringResource(R.string.settings_restore_brand_color))
                }
            }

            SettingsSection(stringResource(R.string.settings_accessibility_section))
            SettingsSwitch(
                title = stringResource(R.string.settings_reduce_motion),
                subtitle = stringResource(R.string.settings_reduce_motion_sub),
                checked = settings.reduceMotion,
                onChange = viewModel::setReduceMotion,
            )

            SettingsSection(stringResource(R.string.settings_library_section))
            state.sources.forEach { source ->
                SourceRow(
                    source = source,
                    onRemove = { deleteBooks -> viewModel.removeSource(source, deleteBooks) },
                )
            }
            if (state.sources.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_folders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.settings_add_folder))
            }

            SettingsSection(stringResource(R.string.settings_privacy_section))
            SettingsSwitch(
                title = stringResource(R.string.settings_skip_network_prompt),
                subtitle = stringResource(R.string.settings_skip_network_prompt_sub),
                checked = settings.metadataNetworkAllowed,
                onChange = viewModel::setMetadataAllowed,
            )
            Text(
                text = stringResource(R.string.settings_privacy_statement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )

            SettingsSection(stringResource(R.string.settings_about_section))
            Text("ApriReader ${state.versionName}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = viewModel::clearCache) { Text(stringResource(R.string.settings_clear_cache)) }
            Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SourceRow(source: LibrarySource, onRemove: (Boolean) -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(source.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (source.available) stringResource(R.string.shelf_books_count, source.bookCount) else stringResource(R.string.source_lost_one),
                style = MaterialTheme.typography.bodySmall,
                color = if (source.available) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (!source.available) {
            Icon(
                Icons.Rounded.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.settings_disconnect)) }
    }
    HorizontalDivider()

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_disconnect_dialog_title)) },
            text = {
                Text(stringResource(R.string.settings_disconnect_dialog_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onRemove(false)
                }) { Text(stringResource(R.string.settings_keep_books)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirm = false
                    onRemove(true)
                }) { Text(stringResource(R.string.settings_remove_books)) }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val themeModes = listOf(
    ThemeMode.SYSTEM to R.string.settings_theme_system,
    ThemeMode.LIGHT to R.string.settings_theme_light,
    ThemeMode.DARK to R.string.settings_theme_dark_material,
    ThemeMode.AMOLED to R.string.settings_theme_amoled,
)

private val designStyles = listOf(
    com.aprireader.app.data.prefs.DesignStyle.LIQUID_GLASS to R.string.settings_design_style_liquid_glass,
    com.aprireader.app.data.prefs.DesignStyle.GLASSMORPHISM to R.string.settings_design_style_glassmorphism,
    com.aprireader.app.data.prefs.DesignStyle.SOLID_CLEAN to R.string.settings_design_style_solid_clean,
    com.aprireader.app.data.prefs.DesignStyle.NEUMORPHISM to R.string.settings_design_style_neumorphism,
    com.aprireader.app.data.prefs.DesignStyle.WOOD_LIBRARY to R.string.settings_design_style_wood_library,
)

private val scrollModes = listOf(
    com.aprireader.app.data.prefs.ReadingScrollMode.CONTINUOUS_VERTICAL to R.string.reader_scroll_continuous_vertical,
    com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL to R.string.reader_scroll_paged_horizontal,
    com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_VERTICAL to R.string.reader_scroll_paged_vertical,
)

@Composable
private fun ProfileCard(
    settings: AppSettings,
    onEditProfile: () -> Unit,
) {
    val activeTitle = ReaderTitle.fromKey(settings.userTitleKey)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleMd)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), SquircleMd)
            .clickable(onClick = onEditProfile)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                UserAvatar(
                    avatarId = settings.userAvatarId,
                    customAvatarPath = settings.customAvatarPath,
                    modifier = Modifier.size(68.dp),
                    shape = SquircleLg,
                    borderWidth = 2.dp,
                    borderColor = MaterialTheme.colorScheme.primary,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.surface, SquircleSm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = activeTitle.badgeIcon, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                val displayName = if (settings.userName.isNotBlank()) settings.userName else stringResource(R.string.app_name)
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(activeTitle.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (settings.userBio.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "\"${settings.userBio}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onEditProfile) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.settings_edit_action),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
