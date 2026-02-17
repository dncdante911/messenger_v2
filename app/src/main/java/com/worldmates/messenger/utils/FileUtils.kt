package com.worldmates.messenger.utils

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

/**
 * Утиліти для роботи з файлами
 */
object FileUtils {

    private const val TAG = "FileUtils"

    /**
     * Отримати File з Uri
     * Підтримує різні типи Uri (content://, file://)
     */
    fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    // file:// URI - просто повертаємо File
                    File(uri.path ?: return null)
                }
                "content" -> {
                    // content:// URI - копіюємо в тимчасовий файл
                    copyUriToTempFile(context, uri)
                }
                else -> {
                    Log.e(TAG, "Unsupported URI scheme: ${uri.scheme}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file from URI: ${e.message}", e)
            null
        }
    }

    /**
     * Копіює вміст Uri в тимчасовий файл
     */
    private fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Отримуємо ім'я та розширення файлу
            val fileName = getFileName(context, uri) ?: "temp_${System.currentTimeMillis()}"
            val extension = getFileExtension(fileName)

            // Створюємо тимчасовий файл
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.$extension")

            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            Log.d(TAG, "Файл скопійовано: ${tempFile.absolutePath}, розмір: ${tempFile.length()}")
            tempFile
        } catch (e: IOException) {
            Log.e(TAG, "Error copying URI to temp file", e)
            null
        }
    }

    /**
     * Отримати ім'я файлу з Uri
     */
    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    /**
     * Отримати розширення файлу
     */
    fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0) {
            fileName.substring(lastDot + 1)
        } else {
            "tmp"
        }
    }

    /**
     * Отримати розмір файлу з Uri
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        return it.getLong(sizeIndex)
                    }
                }
            }
            -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size", e)
            -1L
        }
    }

    /**
     * Отримати MIME тип файлу
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }

    /**
     * Перевірити, чи є Uri відео
     */
    fun isVideo(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        return mimeType?.startsWith("video/") == true
    }

    /**
     * Перевірити, чи є Uri зображення
     */
    fun isImage(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        return mimeType?.startsWith("image/") == true
    }

    /**
     * Видалити тимчасові файли старші за вказаний час
     */
    fun cleanupTempFiles(context: Context, olderThanMillis: Long = 24 * 60 * 60 * 1000) {
        try {
            val now = System.currentTimeMillis()
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_") && now - file.lastModified() > olderThanMillis) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "Видалено старий тимчасовий файл: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }

    /**
     * Стиснути зображення для завантаження
     * Зменшує розмір файлу для зображень > 1MB
     * @param imageFile Вхідний файл зображення
     * @param maxSizeKB Максимальний розмір в KB (за замовчуванням 15MB = 15360KB)
     * @param quality Якість JPEG (0-100, за замовчуванням 90 - висока якість)
     * @return Стиснутий файл або оригінал якщо стиснення не потрібне
     */
    fun compressImageIfNeeded(
        context: Context,
        imageFile: File,
        maxSizeKB: Int = 15360,  // 15MB за замовчуванням
        quality: Int = 90  // Висока якість за замовчуванням
    ): File {
        return try {
            val fileSizeKB = imageFile.length() / 1024
            Log.d(TAG, "Розмір оригінального файлу: ${fileSizeKB}KB (${fileSizeKB / 1024.0}MB)")

            // Якщо файл менше 1MB, повертаємо оригінал (не стискаємо малі файли)
            if (fileSizeKB <= 1024) {
                Log.d(TAG, "Файл < 1MB, стиснення не потрібне")
                return imageFile
            }

            // Якщо файл вже менший за maxSizeKB, також не стискаємо
            if (fileSizeKB <= maxSizeKB) {
                Log.d(TAG, "Файл вже в межах ліміту ${maxSizeKB}KB, стиснення не потрібне")
                return imageFile
            }

            // Декодуємо зображення
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            // Обчислюємо inSampleSize для зменшення розміру
            // Для файлів > 15MB використовуємо більш агресивне стиснення
            val maxDimension = when {
                fileSizeKB > 20480 -> 2560  // > 20MB: зменшуємо до 2560px
                fileSizeKB > 10240 -> 3840  // > 10MB: зменшуємо до 4K
                else -> 4096  // Інакше: макс 4K
            }
            val inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)

            // Декодуємо з inSampleSize
            val decodingOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, decodingOptions)
                ?: return imageFile

            Log.d(TAG, "Зображення декодовано: ${bitmap.width}x${bitmap.height}, original: ${originalWidth}x${originalHeight}")

            // Виправляємо орієнтацію за EXIF
            val correctedBitmap = fixImageOrientation(imageFile.absolutePath, bitmap)

            // Створюємо стиснутий файл
            val compressedFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")

            FileOutputStream(compressedFile).use { out ->
                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }

            correctedBitmap.recycle()
            if (correctedBitmap != bitmap) {
                bitmap.recycle()
            }

            val compressedSizeKB = compressedFile.length() / 1024
            Log.d(TAG, "Файл стиснуто: ${fileSizeKB}KB → ${compressedSizeKB}KB (${100 - (compressedSizeKB * 100 / fileSizeKB)}% економії)")

            compressedFile
        } catch (e: Exception) {
            Log.e(TAG, "Помилка стиснення зображення", e)
            imageFile
        }
    }

    /**
     * Обчислити inSampleSize для зменшення зображення
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Виправити орієнтацію зображення згідно EXIF даних
     */
    private fun fixImageOrientation(imagePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Помилка виправлення орієнтації", e)
            bitmap
        }
    }
}