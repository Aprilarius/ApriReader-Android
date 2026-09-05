package com.aprireader.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aprireader.app.data.profile.AvatarPreset
import com.aprireader.app.ui.theme.SquircleLg
import java.io.File

@Composable
fun UserAvatar(
    avatarId: String,
    customAvatarPath: String?,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleLg,
    borderWidth: Dp = 1.5.dp,
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
) {
    val isCustom = avatarId == "custom" && !customAvatarPath.isNullOrBlank()

    val customBitmap = remember(customAvatarPath, isCustom) {
        if (isCustom) {
            val path = customAvatarPath ?: return@remember null
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                runCatching {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                }.getOrNull()
            } else {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (isCustom && customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val preset = AvatarPreset.fromId(avatarId)
            Image(
                painter = painterResource(id = preset.drawableRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (borderWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(borderWidth, borderColor, shape)
            )
        }
    }
}
