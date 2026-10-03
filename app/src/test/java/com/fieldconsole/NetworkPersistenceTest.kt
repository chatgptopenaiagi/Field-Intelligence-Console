package com.fieldconsole

import android.app.Application
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.fieldconsole.data.local.database.AppDatabase
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.data.local.entity.WeatherData
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.network.*
import com.google.gson.JsonParser
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NetworkPersistenceTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val name = "network-test-${UUID.randomUUID().toString().take(8)}.db"
    private var db: AppDatabase? = null
    private fun open(): AppDatabase {
        check(name.startsWith("network-test-") && name != "field_console.db")
        return AppDatabase.create(context,name).also { db = it; it.openHelper.writableDatabase }
    }
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.readBytes()
    @After fun cleanup() { db?.close(); check(name.startsWith("network-test-")); context.deleteDatabase(name) }

    private fun seedV2() {
        val schema = JsonParser.parseString(javaClass.getResource("/com.fieldconsole.data.local.database.AppDatabase/2.json")!!.readText()).asJsonObject.getAsJsonObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE,null).use { raw ->
            schema.getAsJsonArray("entities").forEach { element ->
                val entity = element.asJsonObject
                raw.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", entity["tableName"].asString))
            }
            schema.getAsJsonArray("setupQueries").forEach { raw.execSQL(it.asString) }
            raw.execSQL("INSERT INTO field_entries (id,latitude,longitude,accuracy,note,tag,timestamp,placeName,priority,missionId,operator,isFavorite,weatherSnap,photoPath,audioPath) VALUES (41,0,0,12.5,'Synthetic preserved — café','test',12345,'Place','HIGH','synthetic','operator',1,'{}','photo','audio')")
            fun insert(table: String, entity: Any) {
                val values = ContentValues()
                Gson().toJsonTree(entity).asJsonObject.entrySet().forEach { (key,value) ->
                    if (value.asJsonPrimitive.isBoolean) values.put(key, if(value.asBoolean) 1 else 0)
                    else values.put(key,value.asString)
                }
                raw.insertOrThrow(table,null,values)
            }
            insert("weather_cache",WeatherData(cacheKey="synthetic",tempC=12f))
            insert("emergency_services",EmergencyService(id=1,name="Synthetic hospital"))
            raw.version = 2
        }
    }
    @Test fun `authentic exported v2 schema migrates to v3 preserving all metadata caches and next id`() = runBlocking {
        seedV2(); val database = open()
        assertEquals(3,database.openHelper.writableDatabase.version)
        val original = FieldEntry(id=41,latitude=0.0,longitude=0.0,accuracy=12.5f,note="Synthetic preserved — café",tag="test",timestamp=12345,
            placeName="Place",priority="HIGH",missionId="synthetic",operator="operator",isFavorite=true,weatherSnap="{}",photoPath="photo",audioPath="audio")
        assertEquals(original,database.fieldEntryDao().getById(41))
        assertTrue(database.fieldEntryDao().insert(original.copy(id=0)) > 41)
        assertEquals(12f,database.weatherDao().getByKey("synthetic")!!.tempC)
        assertEquals("Synthetic hospital",database.emergencyServiceDao().getNearby(0.0,0.0).single().name)
        val repo = NetworkRepository(database)
        repo.saveEntity(NetworkCodec.newEntity("Synthetic contact"))
        repo.saveReport(NetworkCodec.parseJson(String(fixture("field-report-v1.json"))))
        assertEquals(1,repo.observeEntities().first().size); assertEquals(1,repo.observeReports().first().size)
    }
    @Test fun `preview cancel atomic import duplicate import and reopen preserve ancestry`() = runBlocking {
        var database = open(); var repo = NetworkRepository(database)
        val preview = NetworkCodec.parse(fixture("contacts.vcf"),"vcf")
        assertTrue(repo.observeEntities().first().isEmpty())
        assertEquals(ImportCounts(0,0),repo.importSelected(preview, emptySet()))
        val selected = preview.entities.map { it["id"].asString }.toSet()
        assertEquals(ImportCounts(2,0),repo.importSelected(preview,selected))
        assertEquals(ImportCounts(0,2),repo.importSelected(preview,selected))
        val expected = repo.observeEntities().first()
        database.close(); database=open(); repo=NetworkRepository(database)
        assertEquals(expected,repo.observeEntities().first())
        val first=expected.first().deepCopy().apply { addProperty("notes","User correction") }
        repo.saveEntity(first)
        assertEquals(ImportCounts(0,2),repo.importSelected(preview,selected))
        assertTrue(repo.observeEntities().first().any { it["notes"].asString == "User correction" })
        repo.deleteEntity(first["id"].asString); assertEquals(1,repo.observeEntities().first().size)
    }
    @Test fun `failed import transaction never leaves partial contacts`() = runBlocking {
        val database=open(); val repo=NetworkRepository(database)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_network BEFORE INSERT ON network_entities WHEN NEW.displayName='Synthetic Organisation' BEGIN SELECT RAISE(ABORT,'synthetic failure'); END")
        val preview=NetworkCodec.parse(fixture("contacts.vcf"),"vcf")
        var failed=false
        try { repo.importSelected(preview,preview.entities.map { it["id"].asString }.toSet()) } catch (_:Exception) { failed=true }
        assertTrue(failed); assertTrue(repo.observeEntities().first().isEmpty())
    }
    @Test fun `malformed v2 migration fails closed and preserves original database`() {
        seedV2()
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { it.execSQL("CREATE TABLE network_entities(unexpected TEXT)") }
        assertThrows(IllegalStateException::class.java) { open() }; db?.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertEquals(2,raw.version)
            raw.rawQuery("SELECT note FROM field_entries WHERE id=41",null).use { assertTrue(it.moveToFirst()); assertEquals("Synthetic preserved — café",it.getString(0)) }
        }
    }
}
