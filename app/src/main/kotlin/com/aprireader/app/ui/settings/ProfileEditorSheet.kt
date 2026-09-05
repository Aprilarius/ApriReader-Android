package com.aprireader.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aprireader.app.R
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.profile.AvatarGender
import com.aprireader.app.data.profile.AvatarPreset
import com.aprireader.app.data.profile.ReaderTitle
import com.aprireader.app.ui.components.UserAvatar
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquircleSm

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateAvatarId: (String) -> Unit,
    onUpdateTitleKey: (String) -> Unit,
    onUpdateBio: (String) -> Unit,
    onPickCustomAvatarUri: (Uri) -> Unit,
    onClearCustomAvatar: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var tabIndex by remember {
        val initialPreset = AvatarPreset.fromId(settings.userAvatarId)
        val initialTab = if (settings.userAvatarId == "custom") {
            2
        } else if (initialPreset.gender == AvatarGender.MALE) {
            0
        } else {
            1
        }
        mutableIntStateOf(initialTab)
    }

    var currentName by remember(settings.userName) { mutableStateOf(settings.userName) }
    var currentBio by remember(settings.userBio) { mutableStateOf(settings.userBio) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickCustomAvatarUri(uri)
            tabIndex = 2
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = SquircleLg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.profile_edit_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            // Large Avatar Preview with Title Badge & Live Preview
            val activeTitle = ReaderTitle.fromKey(settings.userTitleKey)
            Box(contentAlignment = Alignment.BottomEnd) {
                UserAvatar(
                    avatarId = settings.userAvatarId,
                    customAvatarPath = settings.customAvatarPath,
                    modifier = Modifier.size(96.dp),
                    shape = SquircleLg,
                    borderWidth = 3.dp,
                    borderColor = MaterialTheme.colorScheme.primary,
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, SquircleSm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = activeTitle.badgeIcon, style = MaterialTheme.typography.titleSmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = currentName.ifBlank { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(activeTitle.titleRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(16.dp))

            // Avatar Tabs: Мужские (5) / Женские (5) / Своё фото
            PrimaryTabRow(
                selectedTabIndex = tabIndex,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("👦 ${stringResource(R.string.profile_avatar_male)}") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("👧 ${stringResource(R.string.profile_avatar_female)}") },
                )
                Tab(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    text = { Text("📸 ${stringResource(R.string.profile_avatar_custom)}") },
                )
            }

            Spacer(Modifier.height(14.dp))

            // Avatar Options Row / Custom Photo Area
            when (tabIndex) {
                0 -> {
                    // Male Avatars (5)
                    val malePresets = AvatarPreset.entries.filter { it.gender == AvatarGender.MALE }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(malePresets) { preset ->
                            val selected = settings.userAvatarId == preset.id
                            AvatarChoiceCard(
                                preset = preset,
                                selected = selected,
                                onClick = { onUpdateAvatarId(preset.id) },
                            )
                        }
                    }
                }
                1 -> {
                    // Female Avatars (5)
                    val femalePresets = AvatarPreset.entries.filter { it.gender == AvatarGender.FEMALE }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(femalePresets) { preset ->
                            val selected = settings.userAvatarId == preset.id
                            AvatarChoiceCard(
                                preset = preset,
                                selected = selected,
                                onClick = { onUpdateAvatarId(preset.id) },
                            )
                        }
                    }
                }
                2 -> {
                    // Custom Photo
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleMd)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), SquircleMd)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (settings.userAvatarId == "custom" && !settings.customAvatarPath.isNullOrBlank()) {
                            UserAvatar(
                                avatarId = "custom",
                                customAvatarPath = settings.customAvatarPath,
                                modifier = Modifier.size(80.dp),
                                shape = SquircleLg,
                                borderWidth = 2.5.dp,
                                borderColor = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    shape = SquircleSm,
                                ) {
                                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.profile_upload_photo))
                                }
                                OutlinedButton(
                                    onClick = onClearCustomAvatar,
                                    shape = SquircleSm,
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.profile_remove_custom_photo))
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.profile_upload_photo),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                shape = SquircleSm,
                            ) {
                                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.profile_upload_photo))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // User Name Field
            OutlinedTextField(
                value = currentName,
                onValueChange = {
                    currentName = it
                    onUpdateName(it)
                },
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                placeholder = { Text(stringResource(R.string.onboarding_name_title)) },
                singleLine = true,
                shape = SquircleSm,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // Reader Title Selection
            Text(
                text = stringResource(R.string.profile_title_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ReaderTitle.entries.forEach { title ->
                    val isSelected = settings.userTitleKey == title.key
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateTitleKey(title.key) },
                        label = { Text("${title.badgeIcon} ${stringResource(title.titleRes)}") },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = SquircleSm,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Reader Bio / Favorite Quote
            OutlinedTextField(
                value = currentBio,
                onValueChange = {
                    currentBio = it
                    onUpdateBio(it)
                },
                label = { Text(stringResource(R.string.profile_bio_label)) },
                placeholder = { Text(stringResource(R.string.profile_bio_placeholder)) },
                maxLines = 3,
                shape = SquircleSm,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                shape = SquircleSm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(android.R.string.ok))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AvatarChoiceCard(
    preset: AvatarPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(104.dp)
            .clip(SquircleMd)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = SquircleMd,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            UserAvatar(
                avatarId = preset.id,
                customAvatarPath = null,
                modifier = Modifier.size(68.dp),
                shape = SquircleSm,
                borderWidth = if (selected) 2.dp else 0.dp,
                borderColor = MaterialTheme.colorScheme.primary,
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(preset.titleRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}
