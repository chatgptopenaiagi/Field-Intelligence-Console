package com.fieldconsole

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldconsole.data.local.database.AppDatabase
import com.fieldconsole.data.local.entity.FieldEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Device gate: uses the reconstructed fixture, not an authenticated historical v1 export. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "migration-device-test-${UUID.randomUUID()}.db"
    private var room: AppDatabase? = null

    @After fun cleanup() {
        check(name.startsWith("migration-device-test-") && name != "field_console.db")
        room?.close()
        context.deleteDatabase(name)
    }

    private fun seed(version: Int) {
        check(name != "field_console.db")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL(instrumentation.context.assets.open("database/reconstructed-v1.sql").bufferedReader().use { reader -> reader.readText() })
            it.execSQL("INSERT INTO field_entries VALUES (41,48.2,16.3,7.5,'retain observation','landmark',123456789)")
            it.version = version
        }
    }

    @Test fun migrateReconstructedV1WithoutDataLoss() = runBlocking {
        seed(1)
        val db = AppDatabase.create(context, name).also { room = it }
        db.openHelper.writableDatabase // Generated Room validation must succeed.
        val expected = FieldEntry(id = 41, latitude = 48.2, longitude = 16.3,
            accuracy = 7.5f, note = "retain observation", tag = "landmark", timestamp = 123456789L)
        assertEquals(expected, db.fieldEntryDao().getById(41))
        assertTrue(db.fieldEntryDao().insert(expected.copy(id = 0)) > 41)
        assertEquals(2, db.openHelper.writableDatabase.version)
    }

    @Test fun unsupportedVersionFailsWithoutRecreation() {
        seed(3)
        val db = AppDatabase.create(context, name).also { room = it }
        assertThrows(IllegalStateException::class.java) { db.openHelper.writableDatabase }
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(3, it.version)
            it.rawQuery("SELECT note FROM field_entries WHERE id=41", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("retain observation", cursor.getString(0))
            }
        }
    }
}
