package com.minhngoc.irremote

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language, independent of the phone's. Android 13+ keeps it as the per-app
 * language (also editable in system settings); older versions store it here and wrap the
 * activity's context with it.
 */
object AppLanguage {
    /** Language tag -> its name in that language. */
    val OPTIONS = linkedMapOf(
        "vi" to "Tiếng Việt", "en" to "English", "ja" to "日本語", "ko" to "한국어", "hi" to "हिन्दी",
        "th" to "ไทย", "lo" to "ລາວ", "id" to "Bahasa Indonesia",
    )

    private val systemLocale: Locale = Locale.getDefault()

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The chosen tag, or null to follow the phone. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.toLanguageTag()
                ?.let { tag -> OPTIONS.keys.firstOrNull { Locale.forLanguageTag(it).language == Locale.forLanguageTag(tag).language } }
        } else {
            prefs(context).getString("language", null)
        }

    fun set(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activity with the new configuration.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit().putString("language", tag).commit()
            activity.recreate()
        }
    }

    /** Context in the chosen language, for Activity.attachBaseContext on Android 12 and older. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val locale = current(base)?.let(Locale::forLanguageTag) ?: systemLocale
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
