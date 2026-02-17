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
     */
    suspend fun uploadMedia(
        accessToken: String,
        mediaType: String,
        filePath: String,
        recipientId: Long? = null,
        groupId: Long? = null,
        isPremium: Boolean = false,
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
            Log.d(TAG, "Розмір файлу: ${fileSize / 1024 / 1024}MB для типу $mediaType")

            if (!validateFileSize(file, mediaType, isPremium)) {
                return@withContext UploadResult.Error("Файл занадто великий для типу: $mediaType")
            }

            // Шаг 1: Загружаем файл на сервер через xhr endpoint
            Log.d(TAG, "Крок 1: Завантаження файлу на сервер...")
            val uploadResponse = uploadFileToServer(accessToken, file, mediaType, onProgress)

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
            val mediaUrl = when (mediaType) {
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

            // Шаг 2: Отправляем сообщение с URL загруженного файла
            if (recipientId != null) {
                Log.d(TAG, "Крок 2: Відправка повідомлення з медіа...")
                val messageHashId = System.currentTimeMillis().toString()
                val messageResponse = RetrofitClient.apiService.sendMessage(
                    accessToken = accessToken,
                    recipientId = recipientId,
                    text = mediaUrl, // Отправляем URL как текст сообщения
                    messageHashId = messageHashId
                )

                when (messageResponse.apiStatus) {
                    200 -> {
                        val firstMessage = messageResponse.allMessages?.firstOrNull()
                        if (firstMessage != null) {
                            Log.d(TAG, "Повідомлення з медіа відправлено успішно")
                            UploadResult.Success(
                                mediaId = firstMessage.id.toString(),
                                url = finalMediaUrl,
                                thumbnail = null
                            )
                        } else {
                            Log.e(TAG, "API повернув 200, але список повідомлень пустий")
                            UploadResult.Error("Повідомлення відправлено, але не отримано відповідь")
                        }
                    }
                    else -> {
                        Log.e(TAG, "Помилка відправки повідомлення: ${messageResponse.errorMessage}")
                        UploadResult.Error(messageResponse.errorMessage ?: "Помилка відправки повідомлення")
                    }
                }
            } else if (groupId != null) {
                Log.d(TAG, "Крок 2: Відправка повідомлення в групу...")
                val messageResponse = RetrofitClient.apiService.sendGroupMessage(
                    accessToken = accessToken,
                    groupId = groupId,
                    text = mediaUrl
                )

                when (messageResponse.apiStatus) {
                    200 -> {
                        Log.d(TAG, "Повідомлення з медіа в групу відправлено успішно")
                        UploadResult.Success(
                            mediaId = messageResponse.messageId?.toString() ?: "",
                            url = finalMediaUrl,
                            thumbnail = null
                        )
                    }
                    else -> {
                        Log.e(TAG, "Помилка відправки повідомлення в групу: ${messageResponse.errorMessage}")
                        UploadResult.Error(messageResponse.errorMessage ?: "Помилка відправки в групу")
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
     * Загружает файл на сервер через соответствующий xhr endpoint
     */
    private suspend fun uploadFileToServer(
        accessToken: String,
        file: File,
        mediaType: String,
        onProgress: ((Int) -> Unit)? = null
    ): XhrUploadResponse {
        val requestBody = ProgressRequestBody(file, getMimeType(mediaType), onProgress)

        // Создаем MultipartBody.Part с правильным именем параметра для каждого типа
        val filePart = when (mediaType) {
            Constants.MESSAGE_TYPE_IMAGE -> {
                MultipartBody.Part.createFormData("image", file.name, requestBody)
            }
            Constants.MESSAGE_TYPE_VIDEO -> {
                MultipartBody.Part.createFormData("video", file.name, requestBody)
            }
            Constants.MESSAGE_TYPE_AUDIO, Constants.MESSAGE_TYPE_VOICE -> {
                MultipartBody.Part.createFormData("audio", file.name, requestBody)
            }
            else -> {
                MultipartBody.Part.createFormData("file", file.name, requestBody)
            }
        }

        // Вызываем соответствующий xhr endpoint
        return when (mediaType) {
            Constants.MESSAGE_TYPE_IMAGE -> {
                RetrofitClient.apiService.uploadImage(
                    accessToken = accessToken,
                    serverKey = com.worldmates.messenger.data.Constants.SERVER_KEY,
                    image = filePart
                )
            }
            Constants.MESSAGE_TYPE_VIDEO -> {
                RetrofitClient.apiService.uploadVideo(
                    accessToken = accessToken,
                    serverKey = com.worldmates.messenger.data.Constants.SERVER_KEY,
                    video = filePart
                )
            }
            Constants.MESSAGE_TYPE_AUDIO, Constants.MESSAGE_TYPE_VOICE -> {
                RetrofitClient.apiService.uploadAudio(
                    accessToken = accessToken,
                    serverKey = com.worldmates.messenger.data.Constants.SERVER_KEY,
                    audio = filePart
                )
            }
            else -> {
                RetrofitClient.apiService.uploadFile(
                    accessToken = accessToken,
                    serverKey = com.worldmates.messenger.data.Constants.SERVER_KEY,
                    file = filePart
                )
            }
        }
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
            val accessTokenBody = accessToken.toRequestBody("text/plain".toMediaType())

            val response = RetrofitClient.apiService.uploadGroupAvatar(
                accessToken = accessTokenBody,
                groupId = groupIdBody,
                avatar = filePart
            )

            when (response.apiStatus) {
                200 -> {
                    if (response.avatarUrl != null) {
                        UploadResult.Success("", response.avatarUrl)
                    } else {
                        UploadResult.Error("Невідповідь від серверу")
                    }
                }
                else -> UploadResult.Error(response.message ?: "Помилка завантаження")
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
                "file",
                file.name,
                requestBody
            )

            val response = RetrofitClient.apiService.uploadUserAvatar(
                accessToken = accessToken,
                file = filePart
            )

            when (response.apiStatus) {
                200 -> {
                    if (response.url != null) {
                        UploadResult.Success(response.mediaId ?: "", response.url)
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

    private fun validateFileSize(file: File, mediaType: String, isPremium: Boolean = false): Boolean {
        val fileSize = file.length()
        val maxSize = when (mediaType) {
            Constants.MESSAGE_TYPE_IMAGE -> Constants.MAX_IMAGE_SIZE // 15MB
            Constants.MESSAGE_TYPE_VIDEO -> Constants.MAX_VIDEO_SIZE // 1GB (с сжатием)
            Constants.MESSAGE_TYPE_AUDIO, Constants.MESSAGE_TYPE_VOICE -> Constants.MAX_AUDIO_SIZE // 100MB
            Constants.MESSAGE_TYPE_FILE -> Constants.MAX_FILE_SIZE // 500MB для любых файлов
            else -> Constants.MAX_FILE_SIZE // 500MB по умолчанию
        }
        Log.d(TAG, "Валідація розміру: ${fileSize / 1024 / 1024}MB / ${maxSize / 1024 / 1024}MB для типу $mediaType")
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
        val fileInputStream = file.inputStream()
        val totalBytes = file.length()
        var uploadedBytes = 0L

        fileInputStream.use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                uploadedBytes += read
                sink.write(buffer, 0, read)
                val progress = (uploadedBytes * 100 / totalBytes).toInt()
                onProgress?.invoke(progress)
            }
        }
    }
}