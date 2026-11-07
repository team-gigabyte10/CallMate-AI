package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ContactDao
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.ExecutionDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.RecurrenceDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.ConversationEntity
import com.example.data.local.entities.ExecutionEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.RecurrenceEntity
import com.example.data.local.entities.TaskEntity
import com.example.domain.model.ActionType
import com.example.domain.model.ExecutionMode
import com.example.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TaskEntity::class,
        ContactEntity::class,
        MessageEntity::class,
        RecurrenceEntity::class,
        ExecutionEntity::class,
        ConversationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CallMateDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun recurrenceDao(): RecurrenceDao
    abstract fun executionDao(): ExecutionDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: CallMateDatabase? = null

        fun getDatabase(context: Context): CallMateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CallMateDatabase::class.java,
                    "callmate_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: CallMateDatabase) {
            val contactDao = database.contactDao()
            val taskDao = database.taskDao()
            val conversationDao = database.conversationDao()

            val contacts = listOf(
                ContactEntity(
                    id = "c1",
                    name = "Rahim Chowdhury",
                    phoneNumber = "+8801711223344",
                    nickname = "Rahim Bhai",
                    isFavorite = true,
                    avatarColor = 0xFF3B82F6,
                    notes = "Lead Developer at TechWave"
                ),
                ContactEntity(
                    id = "c2",
                    name = "Ayesha Siddiqua",
                    phoneNumber = "+8801819556677",
                    nickname = "Ammu",
                    isFavorite = true,
                    avatarColor = 0xFFEC4899,
                    notes = "Mom"
                ),
                ContactEntity(
                    id = "c3",
                    name = "Tanvir Ahmed",
                    phoneNumber = "+8801912345678",
                    nickname = "Boss",
                    isFavorite = false,
                    avatarColor = 0xFF10B981,
                    notes = "Project Manager"
                ),
                ContactEntity(
                    id = "c4",
                    name = "Dr. Farhan Kabir",
                    phoneNumber = "+8801611002233",
                    nickname = "Doctor Farhan",
                    isFavorite = false,
                    avatarColor = 0xFF8B5CF6,
                    notes = "Family Physician"
                ),
                ContactEntity(
                    id = "c5",
                    name = "Nabila Karim",
                    phoneNumber = "+8801512889900",
                    nickname = "Nabila",
                    isFavorite = true,
                    avatarColor = 0xFFF59E0B,
                    notes = "Design partner"
                )
            )
            contactDao.insertContacts(contacts)

            val now = System.currentTimeMillis()
            val oneHour = 3600000L
            val tasks = listOf(
                TaskEntity(
                    id = "t1",
                    contactId = "c1",
                    contactName = "Rahim Chowdhury",
                    contactPhone = "+8801711223344",
                    title = "Discuss API integration sprint",
                    actionType = ActionType.CALL,
                    messageBody = null,
                    scheduledAt = now + (2 * oneHour),
                    executionMode = ExecutionMode.ASSISTED,
                    status = TaskStatus.SCHEDULED
                ),
                TaskEntity(
                    id = "t2",
                    contactId = "c3",
                    contactName = "Tanvir Ahmed",
                    contactPhone = "+8801912345678",
                    title = "Send sprint status update",
                    actionType = ActionType.WHATSAPP,
                    messageBody = "Hi Tanvir, the CallMate AI Android architecture and Room persistence are ready for review.",
                    scheduledAt = now + (4 * oneHour),
                    executionMode = ExecutionMode.ASSISTED,
                    status = TaskStatus.SCHEDULED
                ),
                TaskEntity(
                    id = "t3",
                    contactId = "c2",
                    contactName = "Ayesha Siddiqua",
                    contactPhone = "+8801819556677",
                    title = "Evening check-in call with Ammu",
                    actionType = ActionType.CALL,
                    messageBody = null,
                    scheduledAt = now + (7 * oneHour),
                    executionMode = ExecutionMode.REMINDER,
                    status = TaskStatus.SCHEDULED
                ),
                TaskEntity(
                    id = "t4",
                    contactId = null,
                    contactName = null,
                    contactPhone = null,
                    title = "Review weekly calendar agenda and promises",
                    actionType = ActionType.REMINDER,
                    messageBody = "Check pending follow-ups with team members",
                    scheduledAt = now + (10 * oneHour),
                    executionMode = ExecutionMode.REMINDER,
                    status = TaskStatus.SCHEDULED
                )
            )
            taskDao.insertTasks(tasks)

            conversationDao.insertConversation(
                ConversationEntity(
                    sender = "AI",
                    message = "Welcome to CallMate AI! I can help you schedule calls, prepare WhatsApp messages, draft SMS, and set smart reminders in English, বাংলা, or Banglish. Try saying: \"Call Rahim tomorrow at 2 PM\" or \"Ammu k rat 8 tay call dao\"."
                )
            )
        }
    }
}
