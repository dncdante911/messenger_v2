package com.worldmates.messenger.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit клієнт для Strapi CMS
 * Автоматично додає Authorization Bearer токен до кожного запиту
 */
object StrapiClient {

    private const val BASE_URL = "https://cdn.worldmates.club/"

    /**
     * API токен для доступу до Strapi
     * ВАЖЛИВО: В production середовищі краще зберігати в BuildConfig або secure storage
     */
    private const val API_TOKEN = "4ceaf02aab79d2e264fe90825a8da00b0f388258d98dbf5a75ccc813ceafd252cec850c5e2fdabe8e107eab7df02bf9c83051bd6210f7888a5e858439e03972ad7b0e5048b9b0069a9b16394efcd4879ea3bfcccf8629e05eccc55a363dc82bf6c787388c3af8b751a172e0e86b00c3887269f08c45bee5543876f449986749d"

    /**
     * OkHttp клієнт з автоматичною підстановкою токену
     */
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (com.worldmates.messenger.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $API_TOKEN")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit інстанс для Strapi API
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * API сервіс для доступу до Strapi endpoints
     */
    val api: StrapiApiService by lazy {
        retrofit.create(StrapiApiService::class.java)
    }

    /**
     * CDN URL для побудови повних шляхів до медіа файлів
     */
    val CDN_URL: String = "https://cdn.worldmates.club"
}