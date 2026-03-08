package com.worldmates.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AccountEntity - локально збережений акаунт для мультиаккаунтної системи.
 *
 * Ліміти: Free = 5 акаунтів, Pro = 10 акаунтів.
 * Активний акаунт зберігається прапором isActive.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val userId: Long,

    /** Access token для API-запитів */
    val accessToken: String,

    /** Ім'я користувача (username або ім'я) */
    val username: String?,

    /** URL аватара */
    val avatar: String?,

    /** Преміум статус: 0 = free, 1+ = pro */
    val isPro: Int = 0,

    /** Час додавання акаунту (для сортування) */
    val addedAt: Long = System.currentTimeMillis(),

    /** Чи є цей акаунт активним зараз */
    val isActive: Boolean = false
)
