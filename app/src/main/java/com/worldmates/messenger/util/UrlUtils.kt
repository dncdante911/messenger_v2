package com.worldmates.messenger.util

import com.worldmates.messenger.data.Constants

/**
 * Утилиты для работы с URL
 */
object UrlUtils {
    /**
     * Преобразует относительный путь в полный URL
     * Если путь уже является полным URL (начинается с http:// или https://), возвращает как есть
     */
    fun getFullMediaUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("/") -> "${Constants.MEDIA_BASE_URL.trimEnd('/')}$path"
            else -> "${Constants.MEDIA_BASE_URL.trimEnd('/')}/$path"
        }
    }
}

/**
 * Расширение для String для удобного получения полного URL
 */
fun String?.toFullMediaUrl(): String? = UrlUtils.getFullMediaUrl(this)
