package com.worldmates.messenger.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Менеджер для работы с файлами
 */
class FileManager(private val context: Context) {

    companion object {
        private const val TAG = "FileManager"
    }

    /**
     * Копирует файл из URI в кеш-директорию приложения
     * @param uri URI файла
     * @return File объект или null в случае ошибки
     */
    fun copyUriToCache(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Не удалось открыть input stream для URI: $uri")
                return null
            }

            // Получаем имя файла
            val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"

            // Создаем файл в кеш-директории
            val file = File(context.cacheDir, fileName)

            // Копируем данные
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush() // Убеждаемся что данные записаны
            }

            inputStream.close()

            Log.d(TAG, "Файл скопирован в кеш: ${file.absolutePath}, размер: ${file.length()} байт")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка копирования файла из URI: ${e.message}", e)
            null
        }
    }

    /**
     * Получает имя файла из URI
     */
    fun getFileName(uri: Uri): String? {
        var fileName: String? = null

        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        fileName = cursor.getString(columnIndex)
                    }
                }
            }
        }

        if (fileName == null) {
            fileName = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) {
                    path.substring(cut + 1)
                } else {
                    path
                }
            }
        }

        return fileName
    }

    /**
     * Получает размер файла из URI
     */
    fun getFileSize(uri: Uri): Long {
        var size = 0L

        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (columnIndex != -1) {
                        size = cursor.getLong(columnIndex)
                    }
                }
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { path ->
                val file = File(path)
                size = file.length()
            }
        }

        return size
    }

    /**
     * Получает MIME-тип файла из URI
     */
    fun getMimeType(uri: Uri): String? {
        return if (uri.scheme == "content") {
            context.contentResolver.getType(uri)
        } else {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        }
    }

    /**
     * Определяет тип медиа по MIME-типу (image, video, audio, file)
     */
    fun getMediaType(uri: Uri): String {
        val mimeType = getMimeType(uri) ?: return "file"

        return when {
            mimeType.startsWith("image/") -> "image"
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("audio/") -> "audio"
            else -> "file"
        }
    }

    /**
     * Проверяет, является ли файл изображением
     */
    fun isImage(uri: Uri): Boolean {
        return getMimeType(uri)?.startsWith("image/") == true
    }

    /**
     * Проверяет, является ли файл видео
     */
    fun isVideo(uri: Uri): Boolean {
        return getMimeType(uri)?.startsWith("video/") == true
    }

    /**
     * Проверяет, является ли файл аудио
     */
    fun isAudio(uri: Uri): Boolean {
        return getMimeType(uri)?.startsWith("audio/") == true
    }

    /**
     * Форматирует размер файла в читаемый вид
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Удаляет файл
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Файл удален: ${file.absolutePath}")
                }
                deleted
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления файла: ${e.message}", e)
            false
        }
    }

    /**
     * Очищает кеш-директорию от старых файлов
     */
    fun clearOldCacheFiles(olderThanMillis: Long = 24 * 60 * 60 * 1000) {
        try {
            val cacheDir = context.cacheDir
            val currentTime = System.currentTimeMillis()

            cacheDir.listFiles()?.forEach { file ->
                if (currentTime - file.lastModified() > olderThanMillis) {
                    if (file.delete()) {
                        Log.d(TAG, "Удален старый кеш-файл: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка очистки кеша: ${e.message}", e)
        }
    }
}
