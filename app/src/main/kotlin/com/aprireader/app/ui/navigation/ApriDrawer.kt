package com.aprireader.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aprireader.app.R
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.profile.ReaderTitle
import com.aprireader.app.ui.components.UserAvatar
import com.aprireader.app.ui.theme.SquircleDrawer
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquircleSm

enum class DrawerDestination(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
) {
    LIBRARY(Routes.LIBRARY, R.string.drawer_books, Icons.AutoMirrored.Rounded.MenuBook),
    AUDIOBOOKS(Routes.AUDIOBOOKS, R.string.drawer_audiobooks, Icons.Rounded.Headphones),
    STATS(Routes.STATS, R.string.drawer_stats, Icons.Rounded.BarChart),
    SETTINGS(Routes.SETTINGS, R.string.drawer_settings, Icons.Rounded.Settings),
    ABOUT(Routes.ABOUT, R.string.drawer_about, Icons.Rounded.Info),
}

@Composable
fun ApriDrawer(
    drawerState: DrawerState,
    currentRoute: String,
    settings: AppSettings,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = SquircleDrawer,
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                modifier = Modifier.width(320.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    // Header
                    DrawerHeader(settings = settings, onHeaderClick = { onNavigate(DrawerDestination.SETTINGS.route) })

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))

                    // Destinations
                    DrawerDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(destination.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            },
                            selected = selected,
                            onClick = { onNavigate(destination.route) },
                            shape = SquircleSm,
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Footer with privacy guarantee
                    DrawerFooter()
                }
            }
        },
        content = content,
    )
}

@Composable
private fun DrawerHeader(
    settings: AppSettings,
    onHeaderClick: () -> Unit,
) {
    val activeTitle = ReaderTitle.fromKey(settings.userTitleKey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleMd)
            .clickable(onClick = onHeaderClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            UserAvatar(
                avatarId = settings.userAvatarId,
                customAvatarPath = settings.customAvatarPath,
                modifier = Modifier.size(54.dp),
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
            val displayName = if (settings.userName.isNotBlank()) settings.userName else stringResource(R.string.app_name)
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(activeTitle.titleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DrawerFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = stringResource(R.string.drawer_privacy_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
