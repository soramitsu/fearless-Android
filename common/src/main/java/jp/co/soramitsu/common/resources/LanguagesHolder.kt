package jp.co.soramitsu.common.resources

import jp.co.soramitsu.feature_account_api.domain.model.Language
import javax.inject.Singleton

@Singleton
class LanguagesHolder {

    companion object {
        private val RUSSIAN = Language("ru")
        private val ENGLISH = Language("en")
        private val BASHKIR = Language("ba")
        private val SPANISH = Language("es")
        private val UKRAINIAN = Language("uk")
        private val FRENCH = Language("fr")
        private val JAPANESE = Language("ja")
        private val ITALIAN = Language("it")

        private val availableLanguages = mutableListOf(RUSSIAN, ENGLISH, SPANISH, BASHKIR, UKRAINIAN, FRENCH, JAPANESE, ITALIAN)
    }

    fun getEnglishLang(): Language {
        return ENGLISH
    }

    fun getLanguages(): List<Language> {
        return availableLanguages
    }
}