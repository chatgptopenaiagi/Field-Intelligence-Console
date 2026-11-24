package com.fieldconsole

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.robolectric.RuntimeEnvironment
import com.fieldconsole.data.local.database.AppDatabase
import com.fieldconsole.data.local.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DatabaseSafetyTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    // Short name leaves room for Robolectric's Windows sandbox path and SQLite -wal/-shm suffixes.
    private val name = "migration-test-${UUID.randomUUID().toString().take(8)}.db"
    private val originalNote = "retain observation — café\nsecond line"
    private var database: AppDatabase? = null

    @After fun cleanup() {
        check(name.startsWith("migration-test-") && name != "field_console.db")
        database?.close()
        context.deleteDatabase(name) // Only this test's random database; never field_console.db.
    }

    private fun seedLegacy(version: Int = 1) {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            // Reconstructed from the recovered v1->v2 SQL and FieldEntry's original columns.
            // No authentic v1 schema export or old binary exists in recovered Git history.
            val sql = javaClass.getResource("/database/reconstructed-v1.sql")!!.readText()
            db.execSQL(sql)
            db.execSQL("INSERT INTO field_entries VALUES (?,?,?,?,?,?,?)",
                arrayOf(41L, 48.2, 16.3, 7.5, originalNote, "landmark", 123456789L))
            db.version = version
        }
    }

    private fun open(): AppDatabase {
        check(name.startsWith("migration-test-") && name != "field_console.db")
        check(context.getDatabasePath(name).absolutePath.length < 240) {
            "Test database path is too long for Windows SQLite journal files"
        }
        return AppDatabase.create(context, name).also {
        database = it
        it.openHelper.writableDatabase // Trigger migration AND generated Room schema validation.
        }
    }

    @Test fun `v1 migration preserves every original field and supplies v2 defaults`() = runBlocking {
        seedLegacy()
        val db = open()
        val row = db.fieldEntryDao().getById(41)!!
        assertEquals(FieldEntry(id = 41, latitude = 48.2, longitude = 16.3, accuracy = 7.5f,
            note = originalNote, tag = "landmark", timestamp = 123456789), row)
        db.openHelper.readableDatabase.query("SELECT CAST(note AS BLOB) FROM field_entries WHERE id=41").use {
            assertTrue(it.moveToFirst())
            assertArrayEquals(originalNote.toByteArray(Charsets.UTF_8), it.getBlob(0))
        }
        val next = db.fieldEntryDao().insert(row.copy(id = 0, note = "new observation"))
        assertTrue(next > 41)
        assertEquals(4, db.openHelper.writableDatabase.version)
        db.weatherDao().upsert(WeatherData(cacheKey = "sample", tempC = 9f))
        assertEquals(9f, db.weatherDao().getByKey("sample")!!.tempC)
        db.emergencyServiceDao().insertAll(listOf(EmergencyService(name = "Hospital")))
        assertEquals("Hospital", db.emergencyServiceDao().getNearby(0.0, 0.0).single().name)
    }

    @Test fun `fresh v3 database persists scan and extended metadata across reopen`() = runBlocking {
        var db = open()
        val entry = FieldEntry(id = 50, latitude = 1.0, longitude = 2.0, accuracy = 3.5f,
            note = originalNote, tag = "survey", timestamp = 123456789L, placeName = "Known place",
            priority = "HIGH", missionId = "test", operator = "operator", isFavorite = true,
            weatherSnap = "{\"tempC\":9}", photoPath = "local-photo", audioPath = "local-audio")
        db.fieldEntryDao().insert(entry)
        db.close()
        db = open()
        assertEquals(entry, db.fieldEntryDao().getById(50))
    }

    @Test fun `unsupported database version fails closed and preserves history`() {
        seedLegacy(version = 5)
        assertThrows(IllegalStateException::class.java) { open() }
        database?.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(5, db.version)
            db.rawQuery("SELECT note FROM field_entries WHERE id=41", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(originalNote, cursor.getString(0))
            }
        }
    }

    @Test fun `malformed v1 schema fails validation without losing original row`() {
        seedLegacy()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("CREATE TABLE weather_cache (unexpected TEXT)")
        }
        assertThrows(IllegalStateException::class.java) { open() }
        database?.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(1, db.version) // Failed migration rolled back.
            db.rawQuery("SELECT CAST(note AS BLOB) FROM field_entries WHERE id=41", null).use {
                assertTrue(it.moveToFirst())
                assertArrayEquals(originalNote.toByteArray(Charsets.UTF_8), it.getBlob(0))
            }
        }
    }
}
