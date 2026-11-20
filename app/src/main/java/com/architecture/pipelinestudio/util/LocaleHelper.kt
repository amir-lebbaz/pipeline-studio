package com.architecture.pipelinestudio.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "pipeline_prefs"
    private const val KEY_LANG = "selected_language"

    const val LANG_EN = "en"
    const val LANG_AR = "ar"

    fun getPersistedLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, LANG_EN) ?: LANG_EN
    }

    fun persistLanguage(context: Context, langCode: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()
    }

    fun applyLocale(context: Context, langCode: String): Context {
        persistLanguage(context, langCode)
        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }

    fun onAttach(context: Context): Context {
        val lang = getPersistedLanguage(context)
        return applyLocale(context, lang)
    }
}
