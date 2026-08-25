package com.jrprofessor.sketchly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.jrprofessor.sketchly.data.local.ContactDao
import com.jrprofessor.sketchly.data.local.ContactEntity
import com.jrprofessor.sketchly.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val firestore: FirebaseFirestore,
) {

    fun getContacts(userId: String): Flow<List<ContactEntity>> {
        return contactDao.getAllContacts(userId)
    }

    fun getSketchlyContacts(userId: String): Flow<List<ContactEntity>> {
        return contactDao.getSketchlyContacts(userId)
    }

    fun searchContacts(userId: String, query: String): Flow<List<ContactEntity>> {
        return contactDao.searchContacts(userId, query)
    }

    /**
     * Add contact manually by email or phone.
     * Looks up user in Firestore "users" collection to see if they're registered.
     */
    suspend fun addContact(
        userId: String,
        displayName: String,
        email: String = "",
        phone: String = "",
    ): Result<ContactEntity> {
        return try {
            // Check if there is an existing user in Firestore
            var matchedUid = ""
            var isOnSketchly = false

            if (email.isNotBlank()) {
                val snapshot = firestore.collection("users")
                    .whereEqualTo("email", email.trim().lowercase())
                    .limit(1)
                    .get()
                    .await()
                if (!snapshot.isEmpty) {
                    matchedUid = snapshot.documents.first().id
                    isOnSketchly = true
                }
            }

            if (!isOnSketchly && phone.isNotBlank()) {
                val snapshot = firestore.collection("users")
                    .whereEqualTo("phoneNumber", phone.trim())
                    .limit(1)
                    .get()
                    .await()
                if (!snapshot.isEmpty) {
                    matchedUid = snapshot.documents.first().id
                    isOnSketchly = true
                }
            }

            val contact = ContactEntity(
                id = if (matchedUid.isNotBlank()) matchedUid else UUID.randomUUID().toString(),
                userId = userId,
                contactUserId = matchedUid,
                displayName = displayName.trim(),
                email = email.trim(),
                phoneNumber = phone.trim(),
                source = "manual",
                isOnSketchly = isOnSketchly,
                createdAt = System.currentTimeMillis()
            )

            contactDao.insertOrUpdate(contact)
            Result.success(contact)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContact(contact: ContactEntity) {
        contactDao.delete(contact)
    }

    /**
     * Search global users in Firestore by displayName or email prefix for finding friends
     */
    suspend fun searchGlobalUsers(query: String): List<User> {
        if (query.isBlank()) return emptyList()
        return try {
            val snapshot = firestore.collection("users")
                .orderBy("displayName")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(10)
                .get()
                .await()
            snapshot.toObjects(User::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
