package com.jrprofessor.sketchly.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE userId = :userId ORDER BY displayName ASC")
    fun getAllContacts(userId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE userId = :userId AND isOnSketchly = 1 ORDER BY displayName ASC")
    fun getSketchlyContacts(userId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE userId = :userId AND (displayName LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%') ORDER BY displayName ASC")
    fun searchContacts(userId: String, query: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: String): ContactEntity?

    @Query("DELETE FROM contacts WHERE userId = :userId")
    suspend fun clearUserContacts(userId: String)
}
