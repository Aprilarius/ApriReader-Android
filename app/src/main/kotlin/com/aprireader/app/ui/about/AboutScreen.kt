package com.aprireader.app.ui.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprireader.app.R
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquircleSm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenBook: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.onboarding_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Header with App Identity
            AboutHeader(version = state.version)

            // Tab navigation
            PrimaryScrollableTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) },
            ) {
                AboutTab.entries.forEach { tab ->
                    val titleRes = when (tab) {
                        AboutTab.INFO -> R.string.about_tab_info
                        AboutTab.TOS -> R.string.about_tab_tos
                        AboutTab.PRIVACY -> R.string.about_tab_privacy
                        AboutTab.LICENSES -> R.string.about_tab_licenses
                    }
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = stringResource(titleRes),
                                fontWeight = if (state.selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                when (state.selectedTab) {
                    AboutTab.INFO -> InfoTabContent(
                        onClearCache = viewModel::clearCache,
                        cacheCleared = state.cacheCleared,
                        onOpenGuide = {
                            viewModel.openOrCreateWelcomeGuide { bookId ->
                                onOpenBook(bookId)
                            }
                        },
                    )
                    AboutTab.TOS -> TosTabContent()
                    AboutTab.PRIVACY -> PrivacyTabContent()
                    AboutTab.LICENSES -> LicensesTabContent()
                }
            }
        }
    }
}

@Composable
private fun AboutHeader(version: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(SquircleLg)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "v$version • 100% Offline & Private",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoTabContent(
    onClearCache: () -> Unit,
    cacheCleared: Boolean,
    onOpenGuide: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            shape = SquircleMd,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.about_app_philosophy_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.about_app_philosophy_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            shape = SquircleMd,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.about_features_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                val features = listOf(
                    R.string.about_feature_formats,
                    R.string.about_feature_reading_modes,
                    R.string.about_feature_tts,
                    R.string.about_feature_privacy,
                    R.string.about_feature_customization,
                )
                features.forEach { featRes ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(featRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Button(
            onClick = onOpenGuide,
            shape = SquircleSm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.about_open_guide))
        }

        OutlinedButton(
            onClick = onClearCache,
            shape = SquircleSm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (cacheCleared) stringResource(R.string.about_cache_cleared) else stringResource(R.string.settings_clear_cache))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TosTabContent() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.tos_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.tos_last_updated),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TosSection(
            sectionNumber = "1",
            titleRes = R.string.tos_sec1_title,
            bodyRes = R.string.tos_sec1_body,
        )
        TosSection(
            sectionNumber = "2",
            titleRes = R.string.tos_sec2_title,
            bodyRes = R.string.tos_sec2_body,
        )
        TosSection(
            sectionNumber = "3",
            titleRes = R.string.tos_sec3_title,
            bodyRes = R.string.tos_sec3_body,
        )
        TosSection(
            sectionNumber = "4",
            titleRes = R.string.tos_sec4_title,
            bodyRes = R.string.tos_sec4_body,
        )
        TosSection(
            sectionNumber = "5",
            titleRes = R.string.tos_sec5_title,
            bodyRes = R.string.tos_sec5_body,
        )
        TosSection(
            sectionNumber = "6",
            titleRes = R.string.tos_sec6_title,
            bodyRes = R.string.tos_sec6_body,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TosSection(
    sectionNumber: String,
    titleRes: Int,
    bodyRes: Int,
) {
    Card(
        shape = SquircleMd,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "$sectionNumber. ${stringResource(titleRes)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyTabContent() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = SquircleMd,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.privacy_manifesto_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.privacy_manifesto_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        TosSection(
            sectionNumber = "1",
            titleRes = R.string.privacy_sec1_title,
            bodyRes = R.string.privacy_sec1_body,
        )
        TosSection(
            sectionNumber = "2",
            titleRes = R.string.privacy_sec2_title,
            bodyRes = R.string.privacy_sec2_body,
        )
        TosSection(
            sectionNumber = "3",
            titleRes = R.string.privacy_sec3_title,
            bodyRes = R.string.privacy_sec3_body,
        )
        TosSection(
            sectionNumber = "4",
            titleRes = R.string.privacy_sec4_title,
            bodyRes = R.string.privacy_sec4_body,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LicensesTabContent() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.licenses_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.licenses_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val libs = listOf(
            "Kotlin & Coroutines" to "Apache 2.0 • JetBrains",
            "Jetpack Compose & Material 3" to "Apache 2.0 • Google",
            "AndroidX Room & DataStore" to "Apache 2.0 • Google",
            "Coil" to "Apache 2.0 • Coil-kt",
            "OkHttp" to "Apache 2.0 • Square",
        )

        libs.forEach { (name, license) ->
            Card(
                shape = SquircleSm,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(text = license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
