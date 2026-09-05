package com.aprireader.app.data.fonts

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CustomFont(
    val id: String,
    val name: String,
    val fileName: String,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis(),
)

class CustomFontRepository(private val context: Context) {

    private val fontsDir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
    private val manifestFile = File(fontsDir, "fonts_manifest.json")

    private val _fonts = MutableStateFlow<List<CustomFont>>(emptyList())
    val fonts: StateFlow<List<CustomFont>> = _fonts.asStateFlow()

    init {
        loadManifest()
    }

    private fun loadManifest() {
        if (!manifestFile.exists()) {
            _fonts.value = emptyList()
            return
        }
        try {
            val json = manifestFile.readText()
            val array = JSONArray(json)
            val list = mutableListOf<CustomFont>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.getString("filePath")
                if (File(path).exists()) {
                    list.add(
                        CustomFont(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            fileName = obj.getString("fileName"),
                            filePath = path,
                            addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                        )
                    )
                }
            }
            _fonts.value = list
        } catch (e: Exception) {
            _fonts.value = emptyList()
        }
    }

    private fun saveManifest(list: List<CustomFont>) {
        try {
            val array = JSONArray()
            for (f in list) {
                val obj = JSONObject()
                obj.put("id", f.id)
                obj.put("name", f.name)
                obj.put("fileName", f.fileName)
                obj.put("filePath", f.filePath)
                obj.put("addedAt", f.addedAt)
                array.put(obj)
            }
            manifestFile.writeText(array.toString(2))
        } catch (e: Exception) {
            // Manifest write error
        }
    }

    suspend fun importFont(uri: Uri, contentResolver: ContentResolver): Result<CustomFont> =
        withContext(Dispatchers.IO) {
            runCatching {
                var displayName = "Custom Font"
                var originalName = "font.ttf"

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            val n = cursor.getString(nameIndex)
                            if (!n.isNullOrBlank()) {
                                originalName = n
                                displayName = n.substringBeforeLast(".").replace('_', ' ').replace('-', ' ')
                            }
                        }
                    }
                }

                val id = UUID.randomUUID().toString()
                val safeFileName = "${id}_${originalName.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }}"
                val destFile = File(fontsDir, safeFileName)

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

                val maxFileSize = 25 * 1024 * 1024L
                if (destFile.length() < 100 || destFile.length() > maxFileSize || !isValidFontHeader(destFile)) {
                    destFile.delete()
                    throw IllegalArgumentException("The selected file is not a valid font (supported: TTF, OTF, WOFF)")
                }

                val customFont = CustomFont(
                    id = id,
                    name = displayName,
                    fileName = originalName,
                    filePath = destFile.absolutePath,
                )

                val updatedList = _fonts.value.filter { it.id != id } + customFont
                _fonts.value = updatedList
                saveManifest(updatedList)

                customFont
            }
        }

    private fun isValidFontHeader(file: File): Boolean = runCatching {
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        val magic = (header[0].toInt() and 0xFF shl 24) or
            (header[1].toInt() and 0xFF shl 16) or
            (header[2].toInt() and 0xFF shl 8) or
            (header[3].toInt() and 0xFF)
        magic == 0x00010000 || magic == 0x4F54544F || magic == 0x74746366 || magic == 0x774F4646 || magic == 0x774F4632 || magic == 0x74727565
    }.getOrDefault(false)

    suspend fun deleteFont(id: String): Boolean = withContext(Dispatchers.IO) {
        val target = _fonts.value.firstOrNull { it.id == id } ?: return@withContext false
        val file = File(target.filePath)
        if (file.exists()) {
            file.delete()
        }
        val updated = _fonts.value.filter { it.id != id }
        _fonts.value = updated
        saveManifest(updated)
        true
    }
}
