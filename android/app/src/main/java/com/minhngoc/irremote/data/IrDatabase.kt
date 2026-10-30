package com.minhngoc.irremote.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

data class Category(val id: Long, val key: String, val name: String, val codesetCount: Int)
data class Brand(val id: Long, val name: String, val codesetCount: Int)
data class Codeset(
    val id: Long,
    val uid: String,
    val name: String,
    val brandName: String,
    val categoryKey: String,
    val source: String,
    val buttonCount: Int,
)
/** Anything a remote key can stand for: an IR button, or a key sent over the network. */
interface KeySpec {
    val id: Long
    val funcKey: String?
    val label: String
}

data class IrButton(
    override val id: Long,
    override val funcKey: String?,
    override val label: String,
    val freq: Int,
    val pattern: IntArray,
    val repeat: IntArray?,
    val params: String?,
) : KeySpec
data class Source(val name: String, val url: String, val license: String, val revision: String)

/**
 * Read-only access to the bundled code database (assets/irdb.sqlite, built by
 * pipeline/build_db.py). The asset is copied to internal storage on first use
 * and again whenever the app is updated.
 */
class IrDatabase private constructor(private val db: SQLiteDatabase) {

    fun categories(): List<Category> = db.rawQuery(
        """SELECT c.id, c.key, c.name_vi, COALESCE(SUM(cb.codeset_count), 0)
           FROM categories c LEFT JOIN category_brands cb ON cb.category_id = c.id
           GROUP BY c.id ORDER BY c.sort""", null,
    ).use { c -> buildList { while (c.moveToNext()) add(Category(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3))) } }

    fun brands(categoryId: Long, query: String = ""): List<Brand> = db.rawQuery(
        """SELECT b.id, b.name, cb.codeset_count FROM category_brands cb JOIN brands b ON b.id = cb.brand_id
           WHERE cb.category_id = ? AND b.name LIKE ? ORDER BY (b.norm = 'generic'), cb.codeset_count DESC, b.name""",
        arrayOf(categoryId.toString(), "%${query.trim()}%"),
    ).use { c -> buildList { while (c.moveToNext()) add(Brand(c.getLong(0), c.getString(1), c.getInt(2))) } }

    /** Codesets for the matching wizard, most likely first. */
    fun codesets(categoryId: Long, brandId: Long): List<Codeset> = queryCodesets(
        "cs.category_id = ? AND cs.brand_id = ? ORDER BY cs.rank DESC, cs.id",
        arrayOf(categoryId.toString(), brandId.toString()),
    )

    fun codeset(id: Long): Codeset? = queryCodesets("cs.id = ?", arrayOf(id.toString())).firstOrNull()

    /** Codeset ids change whenever the database is rebuilt; saved devices are resolved by uid. */
    fun codesetId(uid: String): Long? = db.rawQuery("SELECT id FROM codesets WHERE uid = ?", arrayOf(uid))
        .use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    private fun queryCodesets(where: String, args: Array<String>): List<Codeset> = db.rawQuery(
        """SELECT cs.id, cs.uid, cs.name, b.name, c.key, s.name, cs.button_count
           FROM codesets cs JOIN brands b ON b.id = cs.brand_id JOIN categories c ON c.id = cs.category_id
           JOIN sources s ON s.id = cs.source_id WHERE $where""", args,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(Codeset(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getInt(6)))
            }
        }
    }

    fun buttons(codesetId: Long): List<IrButton> = db.rawQuery(
        "SELECT id, func_key, label, freq, pattern, repeat, params FROM buttons WHERE codeset_id = ? ORDER BY id",
        arrayOf(codesetId.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                IrButton(
                    id = c.getLong(0),
                    funcKey = if (c.isNull(1)) null else c.getString(1),
                    label = c.getString(2),
                    freq = c.getInt(3),
                    pattern = decodeVarints(c.getBlob(4)),
                    repeat = if (c.isNull(5)) null else decodeVarints(c.getBlob(5)),
                    params = if (c.isNull(6)) null else c.getString(6),
                ),
            )
        }
    }

    fun sources(): List<Source> = db.rawQuery("SELECT name, url, license, revision FROM sources ORDER BY id", null)
        .use { c -> buildList { while (c.moveToNext()) add(Source(c.getString(0), c.getString(1), c.getString(2), c.getString(3))) } }

    companion object {
        private const val ASSET = "irdb.sqlite"
        @Volatile private var instance: IrDatabase? = null

        fun get(context: Context): IrDatabase = instance ?: synchronized(this) {
            instance ?: open(context.applicationContext).also { instance = it }
        }

        private fun open(context: Context): IrDatabase {
            val file = File(context.noBackupFilesDir, ASSET)
            val prefs = context.getSharedPreferences("irdb", Context.MODE_PRIVATE)
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            if (!file.exists() || prefs.getLong("copied_for", -1) != installed) {
                val tmp = File(file.path + ".tmp")
                context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                check(tmp.renameTo(file)) { "Cannot install $ASSET" }
                prefs.edit().putLong("copied_for", installed).apply()
            }
            return IrDatabase(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
        }

        /** Unsigned LEB128 varints, as written by pipeline/build_db.py. */
        fun decodeVarints(blob: ByteArray): IntArray {
            val out = IntArray(blob.size)
            var n = 0
            var value = 0
            var shift = 0
            for (b in blob) {
                value = value or ((b.toInt() and 0x7F) shl shift)
                if (b.toInt() and 0x80 == 0) {
                    out[n++] = value
                    value = 0
                    shift = 0
                } else {
                    shift += 7
                }
            }
            return out.copyOf(n)
        }
    }
}
