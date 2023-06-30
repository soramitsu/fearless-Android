package jp.co.soramitsu.common.resources

import javax.inject.Singleton
import jp.co.soramitsu.core.model.Language

@Singleton
class LanguagesHolder {

    companion object {
        private val RUSSIAN = Language("ru")
        private val ENGLISH = Language("en")
        private val JAPANESE = Language("ja")

        private val availableLanguages = mutableListOf(RUSSIAN, ENGLISH, JAPANESE)
    }

    fun getEnglishLang(): Language {
        return ENGLISH
    }

    fun getLanguages(): List<Language> {
        return availableLanguages
    }
}
