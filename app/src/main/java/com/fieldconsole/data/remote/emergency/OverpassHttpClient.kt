package com.fieldconsole.data.remote.emergency

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Identifies every Overpass request, without changing query syntax or impersonating a browser. */
object OverpassHttpClient {
    fun create(versionName: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "FieldIntelligenceConsole/$versionName (Android)")
                .build())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()
}
