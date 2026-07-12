package com.example.relaynet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: Message)

    @Query("SELECT * FROM messages ORDER BY originTimestamp ASC")
    fun getAllMessages(): Flow<List<Message>>

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE messageId = :messageId)")
    fun existsById(messageId: String): Boolean
}
