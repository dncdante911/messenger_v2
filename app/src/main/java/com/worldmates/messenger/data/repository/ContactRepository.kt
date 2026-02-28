package com.worldmates.messenger.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.worldmates.messenger.data.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository для работы с контактами устройства
 */
class ContactRepository private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ContactRepository"

        @Volatile
        private var instance: ContactRepository? = null

        fun getInstance(context: Context): ContactRepository {
            return instance ?: synchronized(this) {
                instance ?: ContactRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Получает список всех контактов с устройства
     */
    suspend fun getAllContacts(): Result<List<Contact>> = withContext(Dispatchers.IO) {
        try {
            val contacts = mutableListOf<Contact>()
            val contentResolver = context.contentResolver

            val cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex) ?: continue
                    val phoneNumber = it.getString(phoneIndex)
                    val photoUriString = it.getString(photoIndex)

                    // Получаем email для этого контакта
                    val email = getEmailForContact(id)
                    val organization = getOrganizationForContact(id)

                    contacts.add(
                        Contact(
                            id = id,
                            name = name,
                            phoneNumber = phoneNumber,
                            email = email,
                            photoUri = photoUriString?.let { uri -> Uri.parse(uri) },
                            organization = organization
                        )
                    )
                }
            }

            Result.success(contacts.distinctBy { it.id })
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получает email для контакта по ID
     */
    private fun getEmailForContact(contactId: String): String? {
        val contentResolver = context.contentResolver
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
            arrayOf(contactId),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                return it.getString(emailIndex)
            }
        }

        return null
    }

    /**
     * Получает организацию для контакта по ID
     */
    private fun getOrganizationForContact(contactId: String): String? {
        val contentResolver = context.contentResolver
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
            ContactsContract.Data.CONTACT_ID + " = ? AND " +
                    ContactsContract.Data.MIMETYPE + " = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val orgIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
                return it.getString(orgIndex)
            }
        }

        return null
    }

    /**
     * Поиск контактов по имени или номеру
     */
    suspend fun searchContacts(query: String): Result<List<Contact>> = withContext(Dispatchers.IO) {
        try {
            val allContacts = getAllContacts().getOrThrow()
            val filteredContacts = allContacts.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                        contact.phoneNumber?.contains(query) == true
            }
            Result.success(filteredContacts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
