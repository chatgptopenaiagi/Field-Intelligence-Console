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
import com.google.gson.JsonParser
import com.fieldconsole.network.NetworkRepository
import com.fieldconsole.network.NetworkCodec
import kotlinx.coroutines.flow.first

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
        assertEquals(4, db.openHelper.writableDatabase.version)
    }

    @Test fun unsupportedVersionFailsWithoutRecreation() {
        seed(5)
        val db = AppDatabase.create(context, name).also { room = it }
        assertThrows(IllegalStateException::class.java) { db.openHelper.writableDatabase }
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(5, it.version)
            it.rawQuery("SELECT note FROM field_entries WHERE id=41", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("retain observation", cursor.getString(0))
            }
        }
    }

    @Test fun migrateAuthenticExportedV2ToNetworkV3WithoutDataLoss() = runBlocking {
        val schema = instrumentation.context.assets.open("com.fieldconsole.data.local.database.AppDatabase/2.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { raw ->
            schema.getAsJsonArray("entities").forEach { value ->
                val entity=value.asJsonObject
                raw.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}",entity["tableName"].asString))
            }
            schema.getAsJsonArray("setupQueries").forEach { raw.execSQL(it.asString) }
            raw.execSQL("INSERT INTO field_entries VALUES (41,0,0,12.5,'Synthetic observation','test',12345,'Place','HIGH','synthetic','operator',1,'{}','photo','audio')")
            raw.version=2
        }
        val database=AppDatabase.create(context,name).also { room=it }
        assertEquals(4,database.openHelper.writableDatabase.version)
        val row=database.fieldEntryDao().getById(41)!!
        assertEquals("Synthetic observation",row.note)
        assertEquals("Place",row.placeName)
        assertTrue(row.isFavorite)
        assertTrue(database.fieldEntryDao().insert(row.copy(id=0)) > 41)
        val repository=NetworkRepository(database)
        repository.saveEntity(NetworkCodec.newEntity("Synthetic device contact"))
        assertEquals(1,repository.observeEntities().first().size)
    }
}
