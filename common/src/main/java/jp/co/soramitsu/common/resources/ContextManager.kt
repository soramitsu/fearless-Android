package jp.co.soramitsu.common.resources

import android.content.Context
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.utils.SingletonHolder
import java.util.Locale
import javax.inject.Singleton

@Singleton
class ContextManager private constructor(
    private var context: Context,
    private val languagesHolder: LanguagesHolder
) {

    private val LANGUAGE_PART_INDEX = 0
    private val COUNTRY_PART_INDEX = 1

    companion object : SingletonHolder<ContextManager, Context, LanguagesHolder>(::ContextManager)

    fun getContext(): Context {
        return context
    }

    fun setLocale(context: Context): Context {
        return updateResources(context)
    }

    fun getLocale(): Locale {
        return if (Locale.getDefault().displayLanguage != "ba") Locale.getDefault() else Locale("ru")
    }

    private fun updateResources(context: Context): Context {
        val prefs = PreferencesImpl(context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE))

        val currentLanguage = if (prefs.getCurrentLanguage() == null) {
            val currentLocale = Locale.getDefault()
            if (languagesHolder.getLanguages().map { it.iso }.contains(currentLocale.language)) {
                currentLocale.language
            } else {
                languagesHolder.getEnglishLang().iso
            }
        } else {
            prefs.getCurrentLanguage()!!.iso
        }

        prefs.saveCurrentLanguage(currentLanguage)

        val locale = mapLanguageToLocale(currentLanguage)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        this.context = context.createConfigurationContext(configuration)

        return this.context
    }

    private fun mapLanguageToLocale(language: String): Locale {
        val codes = language.split("_")

        return if (hasCountryCode(codes)) {
            Locale(codes[LANGUAGE_PART_INDEX], codes[COUNTRY_PART_INDEX])
        } else {
            Locale(language)
        }
    }

    private fun hasCountryCode(codes: List<String>) = codes.size != 1
}