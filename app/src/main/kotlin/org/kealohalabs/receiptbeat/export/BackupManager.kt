package org.kealohalabs.receiptbeat.export

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.kealohalabs.receiptbeat.core.data.FileStore
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase

/**
 * Full backup: every table as JSON plus every file, decrypted, in one ZIP. Users always own their data.
 * Tables are read generically, so a new table is backed up without code changes.
 * ponytail: the JSON is built in memory; switch to android.util.JsonWriter if databases grow past tens of MB.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ReceiptDatabase,
    private val store: FileStore
) {
    /** Writes cache/exports/Receipt-Beat-backup-<date>.zip and returns it. */
    suspend fun backup(): File = withContext(Dispatchers.IO) {
        val zip = File(File(context.cacheDir, "exports").apply { mkdirs() }, "Receipt-Beat-backup-${LocalDate.now()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry(DB_ENTRY))
            z.write(databaseJson().toString().toByteArray())
            val root = store.file("")
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val path = f.relativeTo(root).invariantSeparatorsPath
                // A file that no longer decrypts (left over from a crash) is skipped, not fatal.
                runCatching {
                    store.input(path).use { input ->
                        z.putNextEntry(ZipEntry(FILES + path))
                        input.copyTo(z)
                    }
                }
            }
        }
        zip
    }

    fun databaseJson(): JSONObject {
        val tables = JSONObject()
        tableNames().forEach { t ->
            val rows = JSONArray()
            db.query(SimpleSQLiteQuery("SELECT * FROM `$t`")).use { c ->
                while (c.moveToNext()) rows.put(row(c))
            }
            tables.put(t, rows)
        }
        return JSONObject().put("app", "Receipt Beat").put("version", VERSION).put("tables", tables)
    }

    /** Replaces everything on the phone with the backup. Nothing is touched if the file is not a valid backup. */
    suspend fun restore(zipUri: Uri) = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "restore.zip")
        try {
            val input = context.contentResolver.openInputStream(zipUri) ?: throw IOException("Could not open the backup")
            input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            restore(tmp)
        } finally {
            tmp.delete()
        }
    }

    suspend fun restore(zipFile: File) = withContext(Dispatchers.IO) {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(DB_ENTRY) ?: throw IOException("This is not a Receipt Beat backup")
            val json = JSONObject(zip.getInputStream(entry).bufferedReader().readText())
            restoreDatabase(json)
            store.file("").listFiles()?.forEach { it.deleteRecursively() }
            zip.entries().asSequence().filter { it.name.startsWith(FILES) && !it.isDirectory }.forEach { e ->
                val path = e.name.removePrefix(FILES)
                // Never write outside the files folder, whatever the ZIP says.
                if (path.startsWith("/") || path.split('/').any { it == ".." || it.isEmpty() }) return@forEach
                zip.getInputStream(e).use { i -> store.output(path).use { i.copyTo(it) } }
            }
        }
    }

    /** Wipes and refills every table in one transaction, so a bad backup leaves the old data in place. */
    fun restoreDatabase(json: JSONObject) {
        val tables = json.getJSONObject("tables")
        val sql = db.openHelper.writableDatabase
        db.runInTransaction {
            tableNames().forEach { t ->
                val columns = columns(t)
                sql.execSQL("DELETE FROM `$t`")
                val rows = tables.optJSONArray(t) ?: return@forEach
                for (i in 0 until rows.length()) {
                    val o = rows.getJSONObject(i)
                    val cv = ContentValues()
                    // Only known columns, so a crafted backup cannot inject SQL through column names.
                    o.keys().asSequence().filter { it in columns }.forEach { k ->
                        when (val v = o.get(k)) {
                            JSONObject.NULL -> cv.putNull(k)
                            is Int -> cv.put(k, v.toLong())
                            is Long -> cv.put(k, v)
                            is Double -> cv.put(k, v)
                            is Boolean -> cv.put(k, if (v) 1L else 0L)
                            else -> cv.put(k, v.toString())
                        }
                    }
                    sql.insert(t, SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    /** Real data tables. Room's own tables and the search index (rebuilt by triggers) are left out. */
    private fun tableNames(): List<String> = db.query(SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type = 'table'")).use { c ->
        generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
    }.filter { !it.startsWith("sqlite_") && !it.startsWith("ReceiptFts") && it != "room_master_table" && it != "android_metadata" }

    private fun columns(table: String): Set<String> = db.query(SimpleSQLiteQuery("PRAGMA table_info(`$table`)")).use { c ->
        generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toSet()
    }

    private fun row(c: Cursor) = JSONObject().apply {
        for (i in 0 until c.columnCount) {
            put(
                c.getColumnName(i),
                when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP)
                    else -> c.getString(i)
                }
            )
        }
    }

    private companion object {
        const val DB_ENTRY = "database.json"
        const val FILES = "files/"
        const val VERSION = 1
    }
}
