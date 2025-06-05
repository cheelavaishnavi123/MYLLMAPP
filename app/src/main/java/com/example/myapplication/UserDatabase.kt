package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class UserDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "pocketllm_users.db"
        private const val DATABASE_VERSION = 2 // Incremented for schema change
        private const val TABLE_USERS = "users"
        private const val TABLE_CONVERSATIONS = "conversations"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_USER_ID = "user_id"
        private const val COLUMN_MESSAGE = "message"
        private const val COLUMN_IS_USER_PROMPT = "is_user_prompt"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USERNAME TEXT UNIQUE NOT NULL,
                $COLUMN_PASSWORD TEXT NOT NULL
            )
        """.trimIndent()
        val createConversationsTable = """
            CREATE TABLE $TABLE_CONVERSATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_ID INTEGER NOT NULL,
                $COLUMN_MESSAGE TEXT NOT NULL,
                $COLUMN_IS_USER_PROMPT INTEGER NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_USER_ID) REFERENCES $TABLE_USERS($COLUMN_ID)
            )
        """.trimIndent()
        db.execSQL(createUsersTable)
        db.execSQL(createConversationsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add new columns to conversations table
            db.execSQL("ALTER TABLE $TABLE_CONVERSATIONS ADD COLUMN $COLUMN_IS_USER_PROMPT INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE $TABLE_CONVERSATIONS ADD COLUMN $COLUMN_TIMESTAMP INTEGER NOT NULL DEFAULT 0")
            // Update existing rows
            db.execSQL("UPDATE $TABLE_CONVERSATIONS SET $COLUMN_TIMESTAMP = ${System.currentTimeMillis()}")
        }
        // Handle future upgrades
        if (oldVersion > newVersion) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CONVERSATIONS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
            onCreate(db)
        }
    }

    fun addUser(username: String, password: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USERNAME, username)
            put(COLUMN_PASSWORD, password)
        }
        val result = db.insert(TABLE_USERS, null, values)
        db.close()
        return result != -1L
    }

    fun verifyUser(username: String, password: String): Long {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID),
            "$COLUMN_USERNAME = ? AND $COLUMN_PASSWORD = ?",
            arrayOf(username, password),
            null, null, null
        )
        return if (cursor.moveToFirst()) {
            val userId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            cursor.close()
            db.close()
            userId
        } else {
            cursor.close()
            db.close()
            -1L
        }
    }

    fun getUserId(username: String): Long {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID),
            "$COLUMN_USERNAME = ?",
            arrayOf(username),
            null, null, null
        )
        return if (cursor.moveToFirst()) {
            val userId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            cursor.close()
            db.close()
            userId
        } else {
            cursor.close()
            db.close()
            -1L
        }
    }

    fun saveConversation(userId: Long, message: String, isUserPrompt: Boolean = true) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_USER_ID, userId)
                put(COLUMN_MESSAGE, message)
                put(COLUMN_IS_USER_PROMPT, if (isUserPrompt) 1 else 0)
                put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            }
            val result = db.insertWithOnConflict(TABLE_CONVERSATIONS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            db.close()
            Log.d("UserDatabase", "Saved conversation: $message, result=$result")
        } catch (e: Exception) {
            Log.e("UserDatabase", "Error saving conversation: ${e.message}", e)
        }
    }

    fun getConversations(userId: Long): List<ConversationItem> {
        val conversations = mutableListOf<ConversationItem>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_CONVERSATIONS,
                arrayOf(COLUMN_MESSAGE, COLUMN_IS_USER_PROMPT, COLUMN_TIMESTAMP),
                "$COLUMN_USER_ID = ?",
                arrayOf(userId.toString()),
                null, null, "$COLUMN_TIMESTAMP ASC"
            )
            while (cursor.moveToNext()) {
                val message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE))
                val isUserPrompt = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_USER_PROMPT)) == 1
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                conversations.add(ConversationItem(message, isUserPrompt, timestamp))
            }
            cursor.close()
            db.close()
            Log.d("UserDatabase", "Retrieved ${conversations.size} conversations for userId=$userId")
        } catch (e: Exception) {
            Log.e("UserDatabase", "Error retrieving conversations: ${e.message}", e)
        }
        return conversations.distinctBy { it.text + it.timestamp } // Deduplicate
    }

    fun deleteConversations(userId: Long) {
        try {
            val db = writableDatabase
            val rowsDeleted = db.delete(TABLE_CONVERSATIONS, "$COLUMN_USER_ID = ?", arrayOf(userId.toString()))
            db.close()
            Log.d("UserDatabase", "Deleted $rowsDeleted conversations for userId=$userId")
        } catch (e: Exception) {
            Log.e("UserDatabase", "Error deleting conversations: ${e.message}", e)
        }
    }
}

data class ConversationItem(val text: String, val isUserPrompt: Boolean, val timestamp: Long)