package com.fieldconsole.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fieldconsole.data.local.dao.EmergencyServiceDao
import com.fieldconsole.data.local.dao.FieldEntryDao
import com.fieldconsole.data.local.dao.WeatherDao
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.data.local.entity.WeatherData
import com.fieldconsole.data.local.entity.NetworkEntity
import com.fieldconsole.data.local.entity.ExchangeReport
import com.fieldconsole.data.local.dao.NetworkDao

@Database(
    entities  = [FieldEntry::class, WeatherData::class, EmergencyService::class, NetworkEntity::class, ExchangeReport::class],
    version   = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun fieldEntryDao(): FieldEntryDao
    abstract fun weatherDao(): WeatherDao
    abstract fun emergencyServiceDao(): EmergencyServiceDao
    abstract fun networkDao(): NetworkDao

    companion object {
        private const val DB_NAME = "field_console.db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE field_entries ADD COLUMN locationProvider TEXT")
            }
        }

        /** Additive v2 -> v3: original observations and caches remain untouched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS network_entities (id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, searchText TEXT NOT NULL, json TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS exchange_reports (id TEXT NOT NULL PRIMARY KEY, searchText TEXT NOT NULL, json TEXT NOT NULL)")
            }
        }

        /** Migration from v1 (original) to v2 (extended FieldEntry + new tables). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to field_entries
                db.execSQL("ALTER TABLE field_entries ADD COLUMN placeName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_entries ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'")
                db.execSQL("ALTER TABLE field_entries ADD COLUMN missionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_entries ADD COLUMN operator TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_entries ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE field_entries ADD COLUMN weatherSnap TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_entries ADD COLUMN photoPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_entries ADD COLUMN audioPath TEXT NOT NULL DEFAULT ''")

                // Create weather_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS weather_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        latitude REAL NOT NULL DEFAULT 0.0,
                        longitude REAL NOT NULL DEFAULT 0.0,
                        fetchedAt INTEGER NOT NULL DEFAULT 0,
                        tempC REAL NOT NULL DEFAULT 0.0,
                        feelsLikeC REAL NOT NULL DEFAULT 0.0,
                        humidity INTEGER NOT NULL DEFAULT 0,
                        pressureHpa REAL NOT NULL DEFAULT 0.0,
                        windSpeedKmh REAL NOT NULL DEFAULT 0.0,
                        windDirectionDeg INTEGER NOT NULL DEFAULT 0,
                        visibilityKm REAL NOT NULL DEFAULT 0.0,
                        cloudCoverPct INTEGER NOT NULL DEFAULT 0,
                        rainProbabilityPct INTEGER NOT NULL DEFAULT 0,
                        uvIndex REAL NOT NULL DEFAULT 0.0,
                        weatherCode INTEGER NOT NULL DEFAULT 0,
                        weatherDescription TEXT NOT NULL DEFAULT '',
                        sunriseEpoch INTEGER NOT NULL DEFAULT 0,
                        sunsetEpoch INTEGER NOT NULL DEFAULT 0,
                        moonPhase REAL NOT NULL DEFAULT 0.0,
                        precipitationMm REAL NOT NULL DEFAULT 0.0,
                        airQualityIndex INTEGER NOT NULL DEFAULT -1,
                        pm25 REAL NOT NULL DEFAULT 0.0,
                        pm10 REAL NOT NULL DEFAULT 0.0,
                        operationalRating TEXT NOT NULL DEFAULT '',
                        weatherImpact TEXT NOT NULL DEFAULT '',
                        hourlyForecastJson TEXT NOT NULL DEFAULT '',
                        dailyForecastJson TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())

                // Create emergency_services table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS emergency_services (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scanLat REAL NOT NULL DEFAULT 0.0,
                        scanLon REAL NOT NULL DEFAULT 0.0,
                        name TEXT NOT NULL DEFAULT '',
                        type TEXT NOT NULL DEFAULT '',
                        address TEXT NOT NULL DEFAULT '',
                        phone TEXT NOT NULL DEFAULT '',
                        lat REAL NOT NULL DEFAULT 0.0,
                        lon REAL NOT NULL DEFAULT 0.0,
                        distanceM REAL NOT NULL DEFAULT 0.0,
                        travelTimeMin INTEGER NOT NULL DEFAULT 0,
                        isOpen INTEGER NOT NULL DEFAULT 0,
                        is24h INTEGER NOT NULL DEFAULT 0,
                        fetchedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context).also { INSTANCE = it }
            }

        /** Same builder for production and isolated persistence integration tests. */
        internal fun create(context: Context, name: String = DB_NAME): AppDatabase =
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    name
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // Missing or invalid migrations must fail closed; observations are irreplaceable.
                    .build()
    }
}
