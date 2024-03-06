package jp.co.soramitsu.common.resources

import javax.inject.Singleton
import jp.co.soramitsu.core.model.Language

@Singleton
class LanguagesHolder {

    companion object {
        private val RUSSIAN = Language("ru")
        private val ENGLISH = Language("en")
        private val JAPANESE = Language("ja")
        private val CHINESE = Language("zh")
        private val VIETNAMESE = Language("vi")
        private val PORTUGUESE = Language("pt")
        private val TURKISH = Language("tr")
        private val INDONESIAN = Language("id")

        private val availableLanguages = mutableListOf(RUSSIAN, ENGLISH, JAPANESE, CHINESE, VIETNAMESE, PORTUGUESE, TURKISH, INDONESIAN)
    }

    fun getEnglishLang(): Language {
        return ENGLISH
    }

    fun getLanguages(): List<Language> {
        return availableLanguages
    }
}
