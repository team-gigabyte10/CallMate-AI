package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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

@Database(
    entities = [
        TaskEntity::class,
        ContactEntity::class,
        MessageEntity::class,
        RecurrenceEntity::class,
        ExecutionEntity::class,
        ConversationEntity::class
    ],
    version = 2,
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
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
