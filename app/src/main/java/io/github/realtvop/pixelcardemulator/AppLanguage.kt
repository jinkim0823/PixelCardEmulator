package io.github.realtvop.pixelcardemulator

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Languages the user can pick in the app. Android keeps the pick per app, so it also shows in the system's app
 * language settings. Every [localeTag] must be listed in res/xml/locales_config.xml.
 */
enum class AppLanguage(val localeTag: String?, @param:StringRes val label: Int) {
    SystemDefault(null, R.string.language_system_default),
    English("en", R.string.language_english),
    SimplifiedChinese("zh-Hans", R.string.language_simplified_chinese),
}

/** The language picked for this app, or [AppLanguage.SystemDefault] when it follows the device. */
fun Context.currentAppLanguage(): AppLanguage {
    val locales = getSystemService(LocaleManager::class.java).applicationLocales
    if (locales.isEmpty) return AppLanguage.SystemDefault
    val language = locales[0].language
    return AppLanguage.entries.firstOrNull { option ->
        option.localeTag != null && Locale.forLanguageTag(option.localeTag).language == language
    } ?: AppLanguage.SystemDefault
}

/** Switches the app to [language]. Android recreates the running activities to show it. */
fun Context.setAppLanguage(language: AppLanguage) {
    getSystemService(LocaleManager::class.java).applicationLocales = language.localeTag
        ?.let { LocaleList.forLanguageTags(it) }
        ?: LocaleList.getEmptyLocaleList()
}
