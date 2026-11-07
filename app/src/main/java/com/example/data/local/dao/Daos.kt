package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.ConversationEntity
import com.example.data.local.entities.ExecutionEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.RecurrenceEntity
import com.example.data.local.entities.TaskEntity
import com.example.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY scheduledAt ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY scheduledAt ASC")
    fun getTasksByStatus(status: TaskStatus): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduledAt >= :startOfDay AND scheduledAt <= :endOfDay ORDER BY scheduledAt ASC")
    fun getTasksForDay(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduledAt >= :now AND status IN ('SCHEDULED', 'SNOOZED', 'DUE') ORDER BY scheduledAt ASC")
    fun getUpcomingTasks(now: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTaskById(id: String): Flow<TaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: TaskStatus, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET scheduledAt = :newTime, status = 'SNOOZED', updatedAt = :now WHERE id = :id")
    suspend fun snoozeTask(id: String, newTime: Long, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :query || '%' OR nickname LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE LOWER(name) = LOWER(:query) OR LOWER(nickname) = LOWER(:query) LIMIT 1")
    suspend fun findByNameOrNickname(query: String): ContactEntity?

    @Query("SELECT * FROM contacts")
    suspend fun getAllContactsSnapshot(): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY scheduledAt DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE taskId = :taskId")
    suspend fun getMessagesForTask(taskId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)
}

@Dao
interface RecurrenceDao {
    @Query("SELECT * FROM recurrences WHERE taskId = :taskId")
    suspend fun getRecurrenceForTask(taskId: String): RecurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurrence(recurrence: RecurrenceEntity)

    @Query("DELETE FROM recurrences WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)
}

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM executions ORDER BY timestamp DESC")
    fun getAllExecutions(): Flow<List<ExecutionEntity>>

    @Query("SELECT * FROM executions WHERE taskId = :taskId ORDER BY timestamp DESC")
    fun getExecutionsForTask(taskId: String): Flow<List<ExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: ExecutionEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()
}
