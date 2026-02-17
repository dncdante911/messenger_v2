package com.worldmates.messenger.network

import android.util.Log
import com.worldmates.messenger.data.Constants
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/**
 * Простая реализация CookieJar для хранения cookies в памяти
 */
class MemoryCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        cookieStore[host] = cookies.toMutableList()
        Log.d("CookieJar", "Saved ${cookies.size} cookies for $host")
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: emptyList()
        Log.d("CookieJar", "Loaded ${cookies.size} cookies for $host")
        return cookies
    }
}

/**
 * Interceptor для добавления server_key и siteEncryptKey в каждый запрос.
 */
class ApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalHttpUrl = originalRequest.url

        // Добавляем siteEncryptKey в качестве query-параметра 's'
        val newUrl = originalHttpUrl.newBuilder()
            .addQueryParameter("s", Constants.SITE_ENCRYPT_KEY)
            .build()

        // Если это POST запрос, добавляем server_key в тело
        val newRequest = if (originalRequest.method == "POST" && originalRequest.body is FormBody) {
            val formBody = originalRequest.body as FormBody
            val formBodyBuilder = FormBody.Builder()

            // Добавляем server_key первым
            formBodyBuilder.add("server_key", Constants.SERVER_KEY)

            // Копируем все существующие параметры
            for (i in 0 until formBody.size) {
                formBodyBuilder.add(formBody.name(i), formBody.value(i))
            }

            originalRequest.newBuilder()
                .url(newUrl)
                .post(formBodyBuilder.build())
                .build()
        } else if (originalRequest.method == "POST" && originalRequest.body is MultipartBody) {
            // Для multipart запросов (загрузка файлов) добавляем server_key в multipart
            val multipartBody = originalRequest.body as MultipartBody
            val multipartBuilder = MultipartBody.Builder()
            multipartBuilder.setType(multipartBody.type)

            // Добавляем server_key первым
            multipartBuilder.addFormDataPart("server_key", Constants.SERVER_KEY)

            // Копируем все существующие части
            for (i in 0 until multipartBody.size) {
                multipartBuilder.addPart(multipartBody.part(i))
            }

            originalRequest.newBuilder()
                .url(newUrl)
                .post(multipartBuilder.build())
                .build()
        } else {
            originalRequest.newBuilder()
                .url(newUrl)
                .build()
        }

        Log.d("API_REQUEST", "URL: ${newRequest.url}")
        Log.d("API_REQUEST", "Method: ${newRequest.method}")
        Log.d("API_REQUEST", "Headers: ${newRequest.headers}")
        if (originalRequest.method == "POST") {
            Log.d("API_REQUEST", "Body type: ${newRequest.body?.javaClass?.simpleName ?: "null"}")
        }

        return chain.proceed(newRequest)
    }
}

/**
 * Конвертер, который обрабатывает пустые ответы от сервера.
 * Если сервер возвращает пустое тело (content-length: 0),
 * вместо падения с EOFException возвращает объект с ошибкой.
 */
class NullOnEmptyConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val delegate = retrofit.nextResponseBodyConverter<Any>(this, type, annotations)
        return Converter<ResponseBody, Any> { body ->
            if (body.contentLength() == 0L) {
                Log.w("NullOnEmptyConverter", "Сервер повернув порожню відповідь (0 bytes) для типу: ${type.typeName}")
                // Для XhrUploadResponse возвращаем объект с ошибкой
                if (type == XhrUploadResponse::class.java) {
                    Log.e("NullOnEmptyConverter", "Помилка: сервер повернув пусту відповідь для завантаження файлу. " +
                        "Файл може бути завантажено, але сервер не повернув інформацію про файл.")
                    XhrUploadResponse(
                        status = 0,
                        imageUrl = null,
                        imageSrc = null,
                        videoUrl = null,
                        videoSrc = null,
                        audioUrl = null,
                        audioSrc = null,
                        fileUrl = null,
                        fileSrc = null,
                        error = "Сервер повернув порожню відповідь. Можливо, файл успішно завантажено, але сервер не повернув інформацію про файл."
                    )
                } else {
                    // Для других типов пытаемся вернуть пустой JSON объект
                    Log.w("NullOnEmptyConverter", "Обробка порожної відповіді для типу ${type.typeName}")
                    val emptyJson = "{}".toResponseBody(body.contentType())
                    try {
                        delegate.convert(emptyJson)
                    } catch (e: Exception) {
                        Log.e("NullOnEmptyConverter", "Не вдалося обробити порожній відповідь: ${e.message}", e)
                        null
                    }
                }
            } else {
                delegate.convert(body)
            }
        }
    }
}

object RetrofitClient {

    // Логирование HTTP запросов
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("API_LOG", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar()) // Сохраняем cookies між запитами
        .addInterceptor(ApiKeyInterceptor()) // Додаємо server_key першим
        .addInterceptor(loggingInterceptor) // Логуємо після модифікації запиту
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URL)
        .client(client)
        .addConverterFactory(NullOnEmptyConverterFactory()) // Обробка порожніх відповідей ПЕРЕД Gson
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Используем новый интерфейс
    val apiService: WorldMatesApi = retrofit.create(WorldMatesApi::class.java)

    // Bot API сервіс (окремий інтерфейс щоб не розширювати WorldMatesApi)
    val botApiService: BotApi = retrofit.create(BotApi::class.java)
}