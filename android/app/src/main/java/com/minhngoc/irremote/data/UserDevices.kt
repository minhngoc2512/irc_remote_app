package com.minhngoc.irremote.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class UserDevice(
    val id: Long,
    val name: String,
    val codesetId: Long,
    val categoryKey: String,
    val brandName: String,
)

/** The user's saved devices ("Thiết bị của tôi"), kept apart from the read-only code database. */
class UserDevices private constructor(context: Context) :
    SQLiteOpenHelper(context, "user.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE devices (
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 name TEXT NOT NULL,
                 codeset_id INTEGER NOT NULL,
                 category_key TEXT NOT NULL,
                 brand_name TEXT NOT NULL,
                 created_at INTEGER NOT NULL)""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(): List<UserDevice> = readableDatabase.rawQuery(
        "SELECT id, name, codeset_id, category_key, brand_name FROM devices ORDER BY created_at", null,
    ).use { c ->
        buildList { while (c.moveToNext()) add(UserDevice(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getString(4))) }
    }

    fun add(name: String, codeset: Codeset): Long = writableDatabase.insert(
        "devices", null,
        ContentValues().apply {
            put("name", name)
            put("codeset_id", codeset.id)
            put("category_key", codeset.categoryKey)
            put("brand_name", codeset.brandName)
            put("created_at", System.currentTimeMillis())
        },
    )

    fun rename(id: Long, name: String) {
        writableDatabase.update("devices", ContentValues().apply { put("name", name) }, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("devices", "id = ?", arrayOf(id.toString()))
    }

    companion object {
        @Volatile private var instance: UserDevices? = null
        fun get(context: Context): UserDevices = instance ?: synchronized(this) {
            instance ?: UserDevices(context.applicationContext).also { instance = it }
        }
    }
}
