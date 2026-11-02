package com.minhngoc.irremote.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.getStringOrNull

data class UserDevice(
    val id: Long,
    val name: String,
    val codesetId: Long,
    val codesetUid: String?,
    val categoryKey: String,
    val brandName: String,
    /** "ir" = codeset sent by the IR blaster; "atv" (Android TV Remote v2) / "webos" (LG SSAP) = over Wi-Fi at [host]. */
    val kind: String = KIND_IR,
    val host: String? = null,
) {
    val isNetwork: Boolean get() = kind != KIND_IR

    companion object {
        const val KIND_IR = "ir"
        const val KIND_ATV = "atv"
        const val KIND_WEBOS = "webos"
    }
}

/** The user's saved devices ("Thiết bị của tôi"), kept apart from the read-only code database. */
class UserDevices private constructor(context: Context) :
    SQLiteOpenHelper(context, "user.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE devices (
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 name TEXT NOT NULL,
                 codeset_id INTEGER NOT NULL,
                 codeset_uid TEXT,
                 category_key TEXT NOT NULL,
                 brand_name TEXT NOT NULL,
                 created_at INTEGER NOT NULL,
                 kind TEXT NOT NULL DEFAULT 'ir',
                 host TEXT)""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 stored only codeset_id, which is not stable across database builds.
        if (oldVersion < 2) db.execSQL("ALTER TABLE devices ADD COLUMN codeset_uid TEXT")
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE devices ADD COLUMN kind TEXT NOT NULL DEFAULT 'ir'")
            db.execSQL("ALTER TABLE devices ADD COLUMN host TEXT")
        }
    }

    fun all(): List<UserDevice> = readableDatabase.rawQuery(
        "SELECT id, name, codeset_id, codeset_uid, category_key, brand_name, kind, host FROM devices ORDER BY created_at", null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(UserDevice(c.getLong(0), c.getString(1), c.getLong(2), c.getStringOrNull(3), c.getString(4), c.getString(5),
                    c.getString(6), c.getStringOrNull(7)))
            }
        }
    }

    fun add(name: String, codeset: Codeset): Long = writableDatabase.insert(
        "devices", null,
        ContentValues().apply {
            put("name", name)
            put("codeset_id", codeset.id)
            put("codeset_uid", codeset.uid)
            put("category_key", codeset.categoryKey)
            put("brand_name", codeset.brandName)
            put("created_at", System.currentTimeMillis())
        },
    )

    /**
     * A TV controlled over the network; it has no IR codeset. One entry per TV: adding a
     * TV that is already saved (same kind and address) returns the existing entry.
     */
    fun addNetwork(name: String, kind: String, host: String, brand: String): Long =
        findNetwork(kind, host)?.let { existing ->
            // Swap the address-only placeholder from the port sweep for the TV's real name.
            if (existing.name.startsWith("TV LG (") && !name.startsWith("TV LG (")) rename(existing.id, name)
            existing.id
        } ?: writableDatabase.insert(
            "devices", null,
            ContentValues().apply {
                put("name", name)
                put("codeset_id", 0)
                put("category_key", "tv")
                put("brand_name", brand)
                put("created_at", System.currentTimeMillis())
                put("kind", kind)
                put("host", host)
            },
        )

    fun findNetwork(kind: String, host: String): UserDevice? = all().firstOrNull { it.kind == kind && it.host == host }

    /** Drops duplicate network TVs saved by earlier versions, keeping the first one added. */
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL(
                """DELETE FROM devices WHERE kind != 'ir' AND id NOT IN
                   (SELECT MIN(id) FROM devices WHERE kind != 'ir' GROUP BY kind, host)""",
            )
        }
    }

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
