package com.example.data.repository

import com.example.data.local.CallMateDatabase
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.ConversationEntity
import com.example.data.local.entities.ExecutionEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.TaskEntity
import com.example.domain.model.ActionType
import com.example.domain.model.ExecutionMode
import com.example.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class CallMateRepository(private val database: CallMateDatabase) {
    private val taskDao = database.taskDao()
    private val contactDao = database.contactDao()
    private val messageDao = database.messageDao()
    private val executionDao = database.executionDao()
    private val conversationDao = database.conversationDao()

    // --- Tasks ---
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getTasksByStatus(status: TaskStatus): Flow<List<TaskEntity>> =
        taskDao.getTasksByStatus(status)

    fun getTodayTasks(): Flow<List<TaskEntity>> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis
        return taskDao.getTasksForDay(startOfDay, endOfDay)
    }

    fun getUpcomingTasks(now: Long = System.currentTimeMillis()): Flow<List<TaskEntity>> =
        taskDao.getUpcomingTasks(now)

    suspend fun getTaskById(id: String): TaskEntity? = taskDao.getTaskById(id)

    suspend fun insertTask(task: TaskEntity) = taskDao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun updateTaskStatus(id: String, status: TaskStatus) =
        taskDao.updateTaskStatus(id, status)

    suspend fun snoozeTask(id: String, additionalMinutes: Int = 10) {
        val newTime = System.currentTimeMillis() + (additionalMinutes * 60 * 1000L)
        taskDao.snoozeTask(id, newTime)
    }

    suspend fun deleteTask(task: TaskEntity) = taskDao.deleteTask(task)

    suspend fun deleteTaskById(id: String) = taskDao.deleteTaskById(id)

    // --- Contacts ---
    val allContacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()
    val favoriteContacts: Flow<List<ContactEntity>> = contactDao.getFavoriteContacts()

    fun searchContacts(query: String): Flow<List<ContactEntity>> =
        contactDao.searchContacts(query)

    suspend fun getContactById(id: String): ContactEntity? =
        contactDao.getContactById(id)

    suspend fun findContactByNameOrNickname(query: String): ContactEntity? =
        contactDao.findByNameOrNickname(query)

    suspend fun getAllContactsSnapshot(): List<ContactEntity> =
        contactDao.getAllContactsSnapshot()

    suspend fun insertContact(contact: ContactEntity) =
        contactDao.insertContact(contact)

    suspend fun updateContact(contact: ContactEntity) =
        contactDao.updateContact(contact)

    suspend fun deleteContact(contact: ContactEntity) =
        contactDao.deleteContact(contact)

    // --- Conversations (AI Chat) ---
    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    suspend fun insertConversation(conversation: ConversationEntity) =
        conversationDao.insertConversation(conversation)

    suspend fun clearConversations() =
        conversationDao.clearConversations()

    // --- Executions (Audit Log) ---
    val allExecutions: Flow<List<ExecutionEntity>> = executionDao.getAllExecutions()

    suspend fun logExecution(
        taskId: String,
        actionType: ActionType,
        executionMode: ExecutionMode,
        outcome: String,
        details: String? = null
    ) {
        val execution = ExecutionEntity(
            taskId = taskId,
            actionType = actionType,
            executionMode = executionMode,
            resultOutcome = outcome,
            details = details
        )
        executionDao.insertExecution(execution)
    }

    // --- Messages ---
    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()

    suspend fun insertMessage(message: MessageEntity) =
        messageDao.insertMessage(message)

    suspend fun clearAllData() {
        taskDao.deleteAllTasks()
        contactDao.deleteAllContacts()
        conversationDao.clearConversations()
    }
}
