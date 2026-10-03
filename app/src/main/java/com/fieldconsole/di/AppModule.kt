package com.fieldconsole.di

import android.content.Context
import com.fieldconsole.BuildConfig
import com.fieldconsole.data.local.dao.EmergencyServiceDao
import com.fieldconsole.data.local.dao.FieldEntryDao
import com.fieldconsole.data.local.dao.WeatherDao
import com.fieldconsole.data.local.database.AppDatabase
import com.fieldconsole.data.remote.emergency.OverpassApi
import com.fieldconsole.data.remote.emergency.OverpassHttpClient
import com.fieldconsole.data.remote.weather.OpenMeteoApi
import com.fieldconsole.data.repository.EmergencyRepository
import com.fieldconsole.data.repository.FieldEntryRepository
import com.fieldconsole.data.repository.WeatherRepository
import com.fieldconsole.export.FieldReportExporter
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.location.LocationManager
import com.fieldconsole.location.ReverseGeocoder
import com.fieldconsole.prompt.FieldPromptGenerator
import com.fieldconsole.security.ApiKeyStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Core infrastructure ───────────────────────────────────────────────────

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder().setPrettyPrinting().create()

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    // ── DAOs ──────────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideFieldEntryDao(db: AppDatabase): FieldEntryDao = db.fieldEntryDao()

    @Provides @Singleton
    fun provideWeatherDao(db: AppDatabase): WeatherDao = db.weatherDao()

    @Provides @Singleton
    fun provideEmergencyServiceDao(db: AppDatabase): EmergencyServiceDao = db.emergencyServiceDao()

    // ── Retrofit APIs ─────────────────────────────────────────────────────────

    @Provides @Singleton
    @Named("openmeteo")
    fun provideOpenMeteoRetrofit(gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(OpenMeteoApi.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides @Singleton
    fun provideOpenMeteoApi(@Named("openmeteo") retrofit: Retrofit): OpenMeteoApi =
        retrofit.create(OpenMeteoApi::class.java)

    @Provides @Singleton
    @Named("overpass")
    fun provideOverpassRetrofit(gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(OverpassApi.BASE_URL)
            .client(OverpassHttpClient.create(BuildConfig.VERSION_NAME))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides @Singleton
    fun provideOverpassApi(@Named("overpass") retrofit: Retrofit): OverpassApi =
        retrofit.create(OverpassApi::class.java)

    // ── Repositories ──────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideFieldEntryRepository(dao: FieldEntryDao): FieldEntryRepository =
        FieldEntryRepository(dao)

    @Provides @Singleton
    fun provideWeatherRepository(
        api:  OpenMeteoApi,
        dao:  WeatherDao,
        gson: Gson
    ): WeatherRepository = WeatherRepository(api, dao, gson)

    @Provides @Singleton
    fun provideEmergencyRepository(
        api: OverpassApi,
        dao: EmergencyServiceDao
    ): EmergencyRepository = EmergencyRepository(api, dao)

    // ── Security ──────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideApiKeyStore(@ApplicationContext context: Context): ApiKeyStore =
        ApiKeyStore(context)

    // ── Location ──────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideLocationManager(@ApplicationContext context: Context): LocationManager =
        LocationManager(context)

    @Provides @Singleton
    fun provideReverseGeocoder(@ApplicationContext context: Context): ReverseGeocoder =
        ReverseGeocoder(context)

    // ── Business logic ────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideFieldPromptGenerator(): FieldPromptGenerator = FieldPromptGenerator()

    @Provides @Singleton
    fun provideIntentDispatcher(promptGen: FieldPromptGenerator): IntentDispatcher =
        IntentDispatcher(promptGen)

    @Provides @Singleton
    fun provideFieldReportExporter(): FieldReportExporter = FieldReportExporter()
}
