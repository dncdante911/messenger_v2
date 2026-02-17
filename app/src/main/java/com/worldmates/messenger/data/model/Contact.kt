package com.worldmates.messenger.data.model

import android.net.Uri

/**
 * Модель контакта из телефонной книги
 */
data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String?,
    val email: String?,
    val photoUri: Uri?,
    val organization: String? = null
) {
    /**
     * Генерирует vCard (версия 3.0)
     */
    fun toVCard(): String {
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$name")

            // Имя (разбиваем на части если есть пробел)
            val nameParts = name.split(" ", limit = 2)
            if (nameParts.size == 2) {
                appendLine("N:${nameParts[1]};${nameParts[0]};;;")
            } else {
                appendLine("N:$name;;;;")
            }

            // Телефон
            phoneNumber?.let {
                appendLine("TEL;TYPE=CELL:$it")
            }

            // Email
            email?.let {
                appendLine("EMAIL;TYPE=INTERNET:$it")
            }

            // Организация
            organization?.let {
                appendLine("ORG:$it")
            }

            appendLine("END:VCARD")
        }
    }

    /**
     * Форматированное отображение контакта
     */
    fun getDisplayText(): String {
        return buildString {
            append(name)
            phoneNumber?.let { append("\n$it") }
            email?.let { append("\n$it") }
            organization?.let { append("\n$it") }
        }
    }

    companion object {
        /**
         * Парсит vCard строку в объект Contact
         */
        fun fromVCard(vCardString: String): Contact? {
            try {
                val lines = vCardString.lines()
                var name = ""
                var phoneNumber: String? = null
                var email: String? = null
                var organization: String? = null

                for (line in lines) {
                    when {
                        line.startsWith("FN:") -> name = line.substringAfter("FN:")
                        line.startsWith("TEL") -> phoneNumber = line.substringAfter(":")
                        line.startsWith("EMAIL") -> email = line.substringAfter(":")
                        line.startsWith("ORG:") -> organization = line.substringAfter("ORG:")
                    }
                }

                if (name.isEmpty()) return null

                return Contact(
                    id = System.currentTimeMillis().toString(),
                    name = name,
                    phoneNumber = phoneNumber,
                    email = email,
                    photoUri = null,
                    organization = organization
                )
            } catch (e: Exception) {
                return null
            }
        }
    }
}
