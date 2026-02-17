package com.worldmates.messenger.utils

import android.content.Context
import android.util.Log
import com.worldmates.messenger.R
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Утилита для обработки ошибок
 */
object ErrorHandler {
    
    private const val TAG = "ErrorHandler"

    /**
     * Преобразует исключение в читаемое сообщение об ошибке
     */
    fun getErrorMessage(context: Context, throwable: Throwable): String {
        Log.e(TAG, "Error: ${throwable.message}", throwable)
        
        return when (throwable) {
            is ConnectException -> {
                context.getString(R.string.error_no_internet)
            }
            is SocketTimeoutException -> {
                "Тайм-аут з'єднання. Спробуйте пізніше"
            }
            is UnknownHostException -> {
                context.getString(R.string.error_no_internet)
            }
            is HttpException -> {
                when (throwable.code()) {
                    400 -> "Помилка запиту. Перевірте дані"
                    401 -> "Не авторизовано. Увійдіть ще раз"
                    403 -> "Доступ заборонено"
                    404 -> "Ресурс не знайдено"
                    500 -> context.getString(R.string.error_server)
                    503 -> "Сервіс недоступний"
                    else -> "Помилка HTTP: ${throwable.code()}"
                }
            }
            is IOException -> {
                context.getString(R.string.error_no_internet)
            }
            else -> {
                throwable.message ?: context.getString(R.string.error_something_wrong)
            }
        }
    }

    /**
     * Логирует ошибку с деталями
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }

    /**
     * Создает исключение с кастомным сообщением
     */
    fun createCustomException(message: String): Exception {
        return Exception(message)
    }
}

/**
 * Seal класс для представления результата операции
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> exception
        else -> null
    }
}

/**
 * Функция расширения для обработки результата
 */
inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        block(data)
    }
    return this
}

inline fun <T> Result<T>.onError(block: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) {
        block(exception)
    }
    return this
}