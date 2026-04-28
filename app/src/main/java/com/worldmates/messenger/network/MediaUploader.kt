package com.worldmates.messenger.network

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/**
 * Менеджер для загрузки медиа-файлів на сервер
 */
class MediaUploader(private val context: Context) {

    sealed class UploadResult {
        data class Success(val mediaId: String, val url: String, val thumbnail: String? = null) : UploadResult()
        data class Error(val message: String, val exception: Exception? = null) : UploadResult()
        data class Progress(val percent: Int) : UploadResult()
    }

    companion object {
        private const val TAG = "MediaUploader"
    }

    /**
     * Загружает несколько медиа-файлов на сервер (до 15 штук)
     */
    suspend fun uploadMultipleFiles(
        accessToken: String,
        files: List<File>,
        mediaTypes: List<String>,
        recipientId: Long? = null,
        groupId: Long? = null,
        onProgress: ((Int, Int) -> Unit)? = null // (current file index, progress)
    ): List<UploadResult> = withContext(Dispatchers.IO) {
        if (files.size > Constants.MAX_FILES_PER_MESSAGE) {
            return@withContext listOf(UploadResult.Error("Максимум ${Constants.MAX_FILES_PER_MESSAGE} файлів за раз"))
        }

        val results = mutableListOf<UploadResult>()
        files.forEachIndexed { index, file ->
            val mediaType = mediaTypes.getOrNull(index) ?: Constants.MESSAGE_TYPE_FILE
            Log.d(TAG, "Завантаження файлу ${index + 1}/${files.size}: ${file.name}")

            val result = uploadMedia(
                accessToken = accessToken,
                mediaType = mediaType,
                filePath = file.absolutePath,
                recipientId = recipientId,
                groupId = groupId,
                onProgress = { progress ->
                    onProgress?.invoke(index, progress)
                }
            )
            results.add(result)

            // Если загрузка не удалась, можно продолжить или прервать
            if (result is UploadResult.Error) {
                Log.e(TAG, "Помилка завантаження файлу ${file.name}: ${result.message}")
            }
        }
        results
    }

    /**
     * Загружает медиа-файл на сервер (двухшаговый процесс)
     * Шаг 1: Загружаем файл на сервер через xhr endpoint
     * Шаг 2: Отправляем сообщение с URL загруженного файла
     *
     * @param quality   Telegram-like quality tier for video uploads.
     *                  Null = no quality param sent (server uses "auto").
     * @param sendAsFile When true, sends video as type="file" (original, no Android compression).
     *                  The server will compress it in the background if it's large.
     */
    suspend fun uploadMedia(
        accessToken: String,
        mediaType: String,
        filePath: String,
        recipientId: Long? = null,
        groupId: Long? = null,
        isPremium: Boolean = com.worldmates.messenger.data.UserSession.isProActive,
        caption: String = "",
        quality: com.worldmates.messenger.utils.VideoCompressor.Quality? = null,
        sendAsFile: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)

            // Проверяем существование файла
            if (!file.exists()) {
                Log.e(TAG, "Файл не існує: $filePath")
                return@withContext UploadResult.Error("Файл не знайдено: $filePath")
            }

            // Проверяем размер файла
            val fileSize = file.length()
            Log.d(TAG, "Розмір файлу: ${"%.2f".format(fileSize / 1024.0 / 1024.0)}MB (${fileSize / 1024}KB) для типу $mediaType")

            // When sendAsFile=true, override media type to "file" so the server stores it
            // as-is and runs background compression for large video files (movies).
            val effectiveType = if (sendAsFile && mediaType == Constants.MESSAGE_TYPE_VIDEO)
                Constants.MESSAGE_TYPE_FILE else mediaType

            if (!validateFileSize(file, effectiveType, isPremium)) {
                return@withContext UploadResult.Error("Файл занадто великий для типу: $effectiveType")
            }

            // Шаг 1: Загружаем файл на сервер через xhr endpoint
            Log.d(TAG, "Крок 1: Завантаження файлу на сервер... type=$effectiveType quality=${quality?.serverParam ?: "auto"}")
            val uploadResponse = uploadFileToServer(accessToken, file, effectiveType, quality, onProgress)

            // Логирование деталей ответа для отладки
            Log.d(TAG, "Статус завантаження: ${uploadResponse.status}")
            Log.d(TAG, "Помилка: ${uploadResponse.error}")
            Log.d(TAG, "ImageURL: ${uploadResponse.imageUrl}")
            Log.d(TAG, "VideoURL: ${uploadResponse.videoUrl}")
            Log.d(TAG, "AudioURL: ${uploadResponse.audioUrl}")
            Log.d(TAG, "FileURL: ${uploadResponse.fileUrl}")

            if (uploadResponse.status != 200) {
                val errorMessage = uploadResponse.error ?: "Невідома помилка завантаження (статус: ${uploadResponse.status})"
                Log.e(TAG, "Помилка завантаження файлу: $errorMessage")
                return@withContext UploadResult.Error(errorMessage)
            }

            // Получаем URL загруженного файла
            val mediaUrl = when (effectiveType) {
                Constants.MESSAGE_TYPE_IMAGE -> uploadResponse.imageUrl
                Constants.MESSAGE_TYPE_VIDEO -> uploadResponse.videoUrl
                Constants.MESSAGE_TYPE_AUDIO, Constants.MESSAGE_TYPE_VOICE -> uploadResponse.audioUrl
                Constants.MESSAGE_TYPE_FILE -> uploadResponse.fileUrl
                else -> uploadResponse.fileUrl
            }

            if (mediaUrl.isNullOrEmpty()) {
                Log.e(TAG, "Сервер не повернув URL файлу (статус успішний, але URL пустий)")
                Log.d(TAG, "Це може бути зашифрований медіа-файл з веб-версії")
                return@withContext UploadResult.Error("Сервер прийняв файл, але не повернув URL. Можливо, файл зашифровано.")
            }

            Log.d(TAG, "Файл завантажено на сервер: $mediaUrl")

            // Проверяем, нужно ли расшифровать URL (если он начинается с Base64)
            val finalMediaUrl = if (mediaUrl.matches(Regex("^[A-Za-z0-9+/]+=*$")) && mediaUrl.length % 4 == 0) {
                Log.d(TAG, "URL виглядає як Base64, може потребувати розшифровки")
                mediaUrl
            } else {
                mediaUrl
            }

            // Шаг 2: Отправляем сообщение с URL загруженного файла через Node.js
            // Все типы медиа (voice, audio, video, image, file) отправляются через
            // единый Node.js endpoint send-media, который сохраняет в БД и рассылает через Socket.IO.
            val hashId = System.currentTimeMillis().toString()
            val nodeApi = NodeRetrofitClient.api

            if (recipientId != null) {
                Log.d(TAG, "Крок 2: Відправка повідомлення з медіа (тип=$effectiveType)...")
                val nodeResponse = nodeApi.sendMediaMessage(
                    recipientId = recipientId,
                    mediaUrl = finalMediaUrl,
                    mediaType = effectiveType,
                    mediaFileName = file.name,
                    messageHashId = hashId,
                    caption = caption
                )

                when (nodeResponse.apiStatus) {
                    200 -> {
                        val savedId = nodeResponse.messageData?.id ?: nodeResponse.messageId ?: 0L
                        Log.d(TAG, "Повідомлення з медіа відправлено успішно через Node.js (id=$savedId)")
                        UploadResult.Success(
                            mediaId = savedId.toString(),
                            url = finalMediaUrl,
                            thumbnail = null
                        )
                    }
                    else -> {
                        Log.e(TAG, "Помилка відправки повідомлення: ${nodeResponse.errorMessage}")
                        UploadResult.Error(nodeResponse.errorMessage ?: "Помилка відправки повідомлення")
                    }
                }
            } else if (groupId != null) {
                Log.d(TAG, "Крок 2: Відправка повідомлення в групу (тип=$effectiveType)...")
                val nodeResponse = nodeApi.sendMediaMessage(
                    groupId = groupId,
                    mediaUrl = finalMediaUrl,
                    mediaType = effectiveType,
                    mediaFileName = file.name,
                    messageHashId = hashId,
                    caption = caption
                )

                when (nodeResponse.apiStatus) {
                    200 -> {
                        Log.d(TAG, "Повідомлення з медіа в групу відправлено успішно через Node.js")
                        UploadResult.Success(
                            mediaId = nodeResponse.messageId?.toString() ?: "",
                            url = finalMediaUrl,
                            thumbnail = null
                        )
                    }
                    else -> {
                        Log.e(TAG, "Помилка відправки повідомлення в групу: ${nodeResponse.errorMessage}")
                        UploadResult.Error(nodeResponse.errorMessage ?: "Помилка відправки в групу")
                    }
                }
            } else {
                Log.e(TAG, "Не вказано recipient_id або group_id")
                return@withContext UploadResult.Error("Не вказано одержувача")
            }

        } catch (e: IOException) {
            Log.e(TAG, "IO Exception: ${e.message}", e)
            UploadResult.Error("Помилка мережі: ${e.message}", e)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP Exception: ${e.code()}", e)
            UploadResult.Error("HTTP ${e.code()}: ${e.message()}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            UploadResult.Error("Помилка: ${e.message}", e)
        }
    }

    /**
     * Uploads a file to the server via Node.js /api/node/chat/upload
     * (replaces PHP /xhr/upload_*.php endpoints).
     */
    private suspend fun uploadFileToServer(
        accessToken: String,
        file: File,
        mediaType: String,
        quality: com.worldmates.messenger.utils.VideoCompressor.Quality? = null,
        onProgress: ((Int) -> Unit)? = null
    ): XhrUploadResponse {
        val requestBody  = ProgressRequestBody(file, getMimeType(mediaType), onProgress)
        val filePart     = MultipartBody.Part.createFormData("file", file.name, requestBody)
        val typePart     = mediaType.toRequestBody("text/plain".toMediaType())
        val qualityParam = (quality?.serverParam ?: "auto").toRequestBody("text/plain".toMediaType())

        return NodeRetrofitClient.chatUploadApi.uploadChatMedia(
            type    = typePart,
            file    = filePart,
            quality = qualityParam,
        )
    }

    /**
     * Загружает аватар группы
     */
    suspend fun uploadGroupAvatar(
        accessToken: String,
        groupId: Long,
        filePath: String,
        onProgress: ((Int) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)

            if (!file.exists()) {
                return@withContext UploadResult.Error("Файл не знайдено")
            }

            val requestBody = ProgressRequestBody(file, "image/*", onProgress)

            val filePart = MultipartBody.Part.createFormData(
                "avatar",  // API expects "avatar" not "file"
                file.name,
                requestBody
            )

            val groupIdBody = groupId.toString().toRequestBody("text/plain".toMediaType())

            val response = NodeRetrofitClient.groupApi.uploadGroupAvatar(
                groupId = groupIdBody,
                avatar = filePart
            )

            when (response.apiStatus) {
                200 -> {
                    val url = response.url ?: response.group?.avatarUrl
                    if (url != null) {
                        UploadResult.Success("", url)
                    } else {
                        UploadResult.Error("Невідповідь від серверу")
                    }
                }
                else -> UploadResult.Error(response.errorMessage ?: "Помилка завантаження")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading avatar: ${e.message}", e)
            UploadResult.Error("Помилка: ${e.message}", e)
        }
    }

    /**
     * Загружает аватар пользователя
     */
    suspend fun uploadUserAvatar(
        accessToken: String,
        filePath: String,
        onProgress: ((Int) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)

            if (!file.exists()) {
                return@withContext UploadResult.Error("Файл не знайдено")
            }

            val requestBody = ProgressRequestBody(file, "image/*", onProgress)

            val filePart = MultipartBody.Part.createFormData(
                "avatar",
                file.name,
                requestBody
            )

            val response = NodeRetrofitClient.api.uploadAvatar(avatar = filePart)

            when (response.apiStatus) {
                200 -> {
                    val url = response.avatar?.url
                    if (url != null) {
                        UploadResult.Success(response.avatar?.id?.toString() ?: "", url)
                    } else {
                        UploadResult.Error("Невідповідь від серверу")
                    }
                }
                else -> UploadResult.Error(response.errorMessage ?: "Помилка завантаження")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading user avatar: ${e.message}", e)
            UploadResult.Error("Помилка: ${e.message}", e)
        }
    }

    private fun validateFileSize(file: File, mediaType: String, isPremium: Boolean = com.worldmates.messenger.data.UserSession.isProActive): Boolean {
        val fileSize = file.length()
        val maxSize = when (mediaType) {
            Constants.MESSAGE_TYPE_IMAGE -> Constants.MAX_IMAGE_SIZE
            Constants.MESSAGE_TYPE_VIDEO -> Constants.MAX_VIDEO_SIZE
            Constants.MESSAGE_TYPE_AUDIO, Constants.MESSAGE_TYPE_VOICE -> Constants.MAX_AUDIO_SIZE
            Constants.MESSAGE_TYPE_FILE  -> Constants.MAX_FILE_SIZE   // 10 GB — movies allowed
            else -> Constants.MAX_FILE_SIZE
        }
        Log.d(TAG, "Валідація розміру: ${"%.2f".format(fileSize / 1024.0 / 1024.0)}MB / ${maxSize / 1024 / 1024}MB для типу $mediaType")
        return fileSize <= maxSize
    }

    private fun getMimeType(mediaType: String): String {
        return when (mediaType) {
            Constants.MESSAGE_TYPE_IMAGE -> "image/*"
            Constants.MESSAGE_TYPE_VIDEO -> "video/*"
            Constants.MESSAGE_TYPE_AUDIO -> "audio/*"
            Constants.MESSAGE_TYPE_VOICE -> "audio/*"
            Constants.MESSAGE_TYPE_FILE -> "application/*"
            else -> "application/octet-stream"
        }
    }
}

/**
 * RequestBody з прогресом завантаження
 */
private class ProgressRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: ((Int) -> Unit)?
) : RequestBody() {

    override fun contentType() = contentType.toMediaType()

    override fun contentLength() = file.length()

    override fun writeTo(sink: okio.BufferedSink) {
        val totalBytes = file.length()
        if (totalBytes == 0L) {
            onProgress?.invoke(100)
            return
        }
        var uploadedBytes = 0L
        var lastReportedProgress = -1

        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024) // 16 KB чанки для плавнішого прогресу
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                // flush() змушує OkHttp відправляти дані порціями, а не буферизувати всі
                sink.flush()
                uploadedBytes += read
                val progress = (uploadedBytes * 100 / totalBytes).toInt().coerceIn(0, 99)
                // Повідомляємо UI тільки при реальній зміні відсотку
                if (progress != lastReportedProgress) {
                    lastReportedProgress = progress
                    onProgress?.invoke(progress)
                }
            }
        }
        // Гарантуємо 100% тільки після повного запису
        onProgress?.invoke(100)
    }
}