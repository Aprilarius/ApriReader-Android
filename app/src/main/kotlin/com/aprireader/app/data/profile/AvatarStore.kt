package com.aprireader.app.data.profile

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AvatarStore(private val context: Context) {

    private val profileDir = File(context.filesDir, "profile_avatar").apply { mkdirs() }

    suspend fun saveCustomAvatar(uri: Uri, contentResolver: ContentResolver): String =
        withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { input ->
                val originalBitmap = BitmapFactory.decodeStream(input)
                    ?: throw IllegalArgumentException("Cannot decode image")

                val maxDim = 512
                val width = originalBitmap.width
                val height = originalBitmap.height
                val scale = if (width > maxDim || height > maxDim) {
                    maxDim.toFloat() / maxOf(width, height)
                } else {
                    1.0f
                }

                val scaledBitmap = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    originalBitmap
                }

                profileDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("user_custom_avatar_") || file.name == "user_custom_avatar.jpg") {
                        file.delete()
                    }
                }

                val targetFile = File(profileDir, "user_custom_avatar_${System.currentTimeMillis()}.jpg")
                FileOutputStream(targetFile).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                if (scaledBitmap != originalBitmap) {
                    scaledBitmap.recycle()
                }
                originalBitmap.recycle()

                targetFile.absolutePath
            } ?: throw IllegalStateException("Cannot open input stream for avatar URI: $uri")
        }

    suspend fun clearCustomAvatar() = withContext(Dispatchers.IO) {
        profileDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("user_custom_avatar_") || file.name == "user_custom_avatar.jpg") {
                file.delete()
            }
        }
    }
}
