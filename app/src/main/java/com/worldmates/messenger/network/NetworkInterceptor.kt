package com.worldmates.messenger.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Интерцептор для логирования HTTP запросов и ответов
 */
class NetworkInterceptor : Interceptor {
    
    companion object {
        private const val TAG = "NetworkInterceptor"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.nanoTime()

        Log.d(TAG, "-->  REQUEST")
        Log.d(TAG, "URL: ${request.url}")
        Log.d(TAG, "Method: ${request.method}")
        Log.d(TAG, "Headers: ${request.headers}")

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "REQUEST FAILED: ${e.message}", e)
            throw e
        }

        val endTime = System.nanoTime()
        val duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime)

        Log.d(TAG, "<--  RESPONSE")
        Log.d(TAG, "Status Code: ${response.code}")
        Log.d(TAG, "Duration: ${duration}ms")
        Log.d(TAG, "Size: ${response.body?.contentLength() ?: "unknown"} bytes")

        return response
    }
}

/**
 * Фабрика для создания HttpLoggingInterceptor
 */
object HttpLoggingInterceptorFactory {
    fun create(level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Log.d("HttpLogging", message)
        }.apply {
            setLevel(level)
        }
    }
}