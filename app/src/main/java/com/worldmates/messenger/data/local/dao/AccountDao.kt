package com.worldmates.messenger.data.local.dao

import androidx.room.*
import com.worldmates.messenger.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * AccountDao - DAO для управління збереженими акаунтами.
 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY addedAt ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY addedAt ASC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccount(): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int

    @Query("SELECT * FROM accounts WHERE userId = :userId LIMIT 1")
    suspend fun getAccountById(userId: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun deleteAccountById(userId: Long)

    @Query("UPDATE accounts SET isActive = 0")
    suspend fun clearAllActive()

    @Query("UPDATE accounts SET isActive = 1 WHERE userId = :userId")
    suspend fun setActiveAccount(userId: Long)

    @Query("""
        UPDATE accounts
        SET accessToken = :token, username = :username, avatar = :avatar, isPro = :isPro
        WHERE userId = :userId
    """)
    suspend fun updateAccount(
        userId: Long,
        token: String,
        username: String?,
        avatar: String?,
        isPro: Int
    )
}
