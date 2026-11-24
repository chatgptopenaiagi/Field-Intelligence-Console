package com.fieldconsole.location

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.fieldconsole.data.emergency.CountryBoundary
import java.util.Locale

object RegionalLanguage {
    private val supported = setOf("de", "ro", "en")
    private fun prefs(c: Context) = c.getSharedPreferences("fic_regional", Context.MODE_PRIVATE)
    fun mode(c: Context) = prefs(c).getString("language", "auto") ?: "auto"

    internal fun automatic(country: String?, preferred: List<String>): String =
        preferred.firstOrNull { it in supported }
            ?: when (country) { "AT", "DE" -> "de"; "RO" -> "ro"; else -> "en" }

    private fun automatic(country: String?): String {
        val languages = Resources.getSystem().configuration.locales
        return automatic(country, (0 until languages.size()).map { languages[it].language })
    }

    fun attach(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val choice = mode(base)
        val lang = if (choice == "auto") automatic(prefs(base).getString("country", null)) else choice
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(lang)))
        })
    }

    fun select(context: Context, choice: String) {
        require(choice == "auto" || choice in supported)
        prefs(context).edit().putString("language", choice).apply()
        apply(context, if (choice == "auto") automatic(prefs(context).getString("country", null)) else choice)
    }

    fun onFix(context: Context, geo: GeoContext) {
        val country = CountryBoundary.current(geo, System.currentTimeMillis()) ?: return
        prefs(context).edit().putString("country", country).apply()
        if (mode(context) != "auto") return
        if (Build.VERSION.SDK_INT >= 33) {
            val current = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            // A language set in Android's per-app settings takes precedence over travel.
            if (current.isNotEmpty() && current != prefs(context).getString("applied", "")) return
        }
        apply(context, automatic(country))
    }

    private fun apply(context: Context, lang: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            prefs(context).edit().putString("applied", lang).apply()
            if (manager.applicationLocales.toLanguageTags() != lang)
                manager.applicationLocales = LocaleList.forLanguageTags(lang)
        } else if (context.resources.configuration.locales[0].language != lang) {
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().firstOrNull()?.recreate()
        }
    }
}
